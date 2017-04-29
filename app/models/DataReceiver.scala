package models

import akka.actor.{ Actor, ActorLogging, Props, ActorRef }
import javax.xml.ws.Holder
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api._
import akka.actor.actorRef2Scala
import scala.concurrent.ExecutionContext.Implicits.global

case class MonitorTypeData(CODE2: String, VAL: Double, ITEM: String)
case class PipeData(CNO: String, POLNO: String, TIME: String, Data: Seq[MonitorTypeData])

object DataReceiver {
  val props = Props[DataReceiver]
  case object GetRealtimeData
  case object GetHistoryData

  var receiver: ActorRef = _
  def startup() = {
    receiver = Akka.system.actorOf(props, name = "dataReceiver")
    val timer = {
      import scala.concurrent.duration._
      Akka.system.scheduler.schedule(Duration(5, SECONDS), Duration(1, MINUTES), receiver, GetHistoryData)
      Akka.system.scheduler.schedule(Duration(5, SECONDS), Duration(6, MINUTES), receiver, GetRealtimeData)
    }
  }

  def getRealtimeData = {
    receiver ! GetRealtimeData
  }
}

class DataReceiver extends Actor with ActorLogging {
  import DataReceiver._
  import com.github.nscala_time.time.Imports._

  val path = current.path.getAbsolutePath + "/importEPA/"
  def receive = {
    case GetHistoryData =>
      try {
        getHistoryData()
        Logger.info("getHistoryData done")
      } catch {
        case ex: Throwable =>
          Logger.error("getHistoryData failed", ex)
      }
    case GetRealtimeData =>
      try {
        getRealtimeData()
        Logger.info("getRealtimeData done")
      } catch {
        case ex: Throwable =>
          Logger.error("getRealtimeData failed", ex)
      }
  }

  def getHistoryData() {
    import play.api.libs.ws.WS
    import play.api.libs.json._
    implicit val mtDataRead = Json.reads[MonitorTypeData]
    implicit val pipeRead = Json.reads[PipeData]
    import models.ModelHelper._

    def getHourData(fromHead: Boolean) = {
      for {
        m <- Monitor.mvList
        monitor = Monitor.map(m)
        (plantID, pipeID) = monitor.getIDs
      } {
        val date = if (fromHead)
          monitor.getHeadDate
        else
          monitor.getTailDate

        if (DateTime.now() > date.toDateTime(LocalTime.parse("11:00")) + 1.day) {
          val dateStr = date.toString("YYYYMMdd")
          Logger.debug(s"getHourData:$fromHead=>${monitor.indParkName}${monitor.dp_no} $dateStr")
          val url = s"http://cems.ilepb.gov.tw/OpenData/API/Daily/${plantID}/${pipeID}/${dateStr}/Json"
          val f = WS.url(url).get().map {
            response =>
              val result = response.json.validate[Seq[PipeData]]
              result.fold(
                error => {
                  Logger.error(JsError.toJson(error).toString())
                },
                pipeDataSeq => {
                  processData(m, date, pipeDataSeq, fromHead)(Record.HourCollection, Monitor.updateHead, Monitor.updateTail)
                })
          }
          f onFailure {
            case ex: Throwable =>
              ModelHelper.logException(ex)
          }
          waitReadyResult(f)
        }
      }
    }

    //Grow tail...
    getHourData(false)
    //Grow head...
    getHourData(true)
  }

  def getRealtimeData() {
    import play.api.libs.ws.WS
    import play.api.libs.json._
    implicit val mtDataRead = Json.reads[MonitorTypeData]
    implicit val pipeRead = Json.reads[PipeData]
    import models.ModelHelper._

    def getMinData(fromHead: Boolean) = {
      for {
        m <- Monitor.mvList
        monitor = Monitor.map(m)
        (plantID, pipeID) = monitor.getIDs
      } {
        val date = if (fromHead) {
          val headDate = monitor.getMinHeadDate
          if (headDate > DateTime.now().toLocalDate())
            DateTime.now().toLocalDate()
          else
            headDate
        } else
          monitor.getMinTailDate

        if (DateTime.now().toLocalDate() >= date) {
          val dateStr = date.toString("YYYYMMdd")
          Logger.debug(s"getMinData:$fromHead=>${monitor.indParkName}${monitor.dp_no} $dateStr")
          val url = s"http://cems.ilepb.gov.tw/OpenData/API/RealTime/${plantID}/${pipeID}/${dateStr}/Json"
          val f = WS.url(url).get().map {
            response =>
              val result = response.json.validate[Seq[PipeData]]
              result.fold(
                error => {
                  Logger.error(JsError.toJson(error).toString())
                },
                pipeDataSeq => {
                  processData(m, date, pipeDataSeq, fromHead)(Record.MinCollection, Monitor.updateMinHead, Monitor.updateMinTail)
                })
          }
          f onFailure {
            case ex: Throwable =>
              ModelHelper.logException(ex)
          }
          waitReadyResult(f)
        }
      }
    }

    //Grow tail...
    getMinData(false)
    //Grow head...
    getMinData(true)
  }

  def processData(m: Monitor.Value, day: LocalDate,
                  pipeDataSeq: Seq[PipeData],
                  isHead: Boolean)(collection: String,
                                   updateHead: (Monitor.Value, Long) => Unit,
                                   updateTail: (Monitor.Value, Long) => Unit) = {
    def toMonitorType(code: String) = {
      code match {
        case "11" =>
          MonitorType.withName("OP")

        case "22" =>
          MonitorType.withName("SO2")

        case "23" =>
          MonitorType.withName("NOx")

        case "24" =>
          MonitorType.withName("CO")

        case "26" =>
          MonitorType.withName("HCl")

        case "36" =>
          MonitorType.withName("O2")

        case "48" =>
          MonitorType.withName("FLOW")

        case "59" =>
          MonitorType.withName("TEMP")
      }
    }

    import scala.collection.mutable.Map
    import scala.collection.mutable.Queue
    val recordMap = Map.empty[Monitor.Value, Map[DateTime, Map[MonitorType.Value, (Double, String)]]]
    val exRecordMap = Map.empty[Monitor.Value, Map[DateTime, Map[MonitorType.Value, Queue[(Double, String)]]]]

    for (pipeData <- pipeDataSeq) {
      val monitor = Monitor.withName(Monitor.monitorId(pipeData.CNO, pipeData.POLNO))
      if (monitor != m) {
        val msg = s"expected: ${m.toString} vs ${monitor.toString}"
        Logger.error(msg)
        throw new Exception(msg)
      }
      val timeMap = recordMap.getOrElseUpdate(monitor, Map.empty[DateTime, Map[MonitorType.Value, (Double, String)]])

      val time = DateTime.parse(pipeData.TIME, DateTimeFormat.forPattern("HH:mm")).toLocalTime()
      val current = day.toDateTime(time)
      val mtMap = timeMap.getOrElseUpdate(current, Map.empty[MonitorType.Value, (Double, String)])

      for (mtData <- pipeData.Data) {
        val monitorType = toMonitorType(mtData.ITEM.substring(1))
        val value = mtData.VAL.toDouble
        val status = if (mtData.CODE2 == "  ")
          "000"
        else
          "0" + mtData.CODE2

        val mtValue = try {
          mtData.VAL.toDouble
        } catch {
          case _: NumberFormatException =>
            0.0
        }

        if (collection == Record.HourCollection && current.getMinuteOfHour != 0) {
          val exTimeMap = exRecordMap.getOrElseUpdate(monitor, Map.empty[DateTime, Map[MonitorType.Value, Queue[(Double, String)]]])
          val hourTime = current.withMinuteOfHour(0)
          val exMtMap = exTimeMap.getOrElseUpdate(hourTime, Map.empty[MonitorType.Value, Queue[(Double, String)]])
          val exQueue = exMtMap.getOrElse(monitorType, Queue.empty[(Double, String)])
          exQueue.enqueue((mtValue, status))
        } else
          mtMap.put(monitorType, (mtValue, status))
      }
    }

    //avg excluded
    for {
      (monitor, timeMaps) <- exRecordMap
      (dateTime, mtMap) <- timeMaps
      (mt, queue) <- mtMap
    } {
      val hourValue = recordMap(monitor)(dateTime)(mt)
      queue.enqueue(hourValue)
      val statusMap = Map.empty[String, Queue[Double]]
      for (elm <- queue) {
        val statusQ = statusMap.getOrElseUpdate(elm._2, Queue.empty[Double])
        statusQ.enqueue(elm._1)
      }
      val statusKV = {
        val kv = statusMap.maxBy(kv => kv._2.length)
        if (kv._1 == MonitorStatus.NormalStat &&
          statusMap(kv._1).size < statusMap.size * 0.75) {
          //return most status except normal
          val noNormalStatusMap = statusMap - kv._1
          noNormalStatusMap.maxBy(kv => kv._2.length)
        } else
          kv
      }
      val values = statusKV._2
      val avg =
        values.sum / values.length
      recordMap(monitor)(dateTime).put(mt, (avg, statusKV._1))
    }

    val latestMapOpt =
      if (isHead) {
        val f = Record.getLatestRecordMapFuture(collection)
        val latestRecordMap = ModelHelper.waitReadyResult(f)
        val latestMap = latestRecordMap map { x => x._1 -> x._2._1 }
        Some(latestMap)
      } else
        None

    val docs =
      for {
        (monitor, timeMaps) <- recordMap
        filteredTimeMap = timeMaps.filter { tm =>
          if (latestMapOpt.isDefined) {
            val latestMap = latestMapOpt.get
            if (latestMap.contains(monitor)) {
              tm._1 > latestMap(monitor)
            } else
              true
          } else
            true
        }
        dateTime <- filteredTimeMap.keys.toList.sorted
        mtMaps = timeMaps(dateTime) if !mtMaps.isEmpty
      } yield {
        Record.toDocument(monitor, dateTime, mtMaps.toList)
      }

    if (!docs.isEmpty) {
      val f = Record.insertRecords(docs.toSeq)(collection)
      f.onSuccess({
        case x =>
          val millis = day.toDateTimeAtStartOfDay().getMillis

          for (m <- recordMap.keys) {
            if (isHead)
              updateHead(m, millis)
            else
              updateTail(m, millis)

            val mCase = Monitor.map(m)
            if (mCase.head.isEmpty)
              Monitor.updateHead(m, millis)

            if (mCase.tail.isEmpty)
              Monitor.updateTail(m, millis)
          }
      })
    } else
      Logger.warn("No data...")
  }
}