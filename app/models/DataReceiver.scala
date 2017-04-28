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
        Logger.info("GetHistoryData done.")
      } catch {
        case ex: Throwable =>
          Logger.error("GetRealtimeData failed", ex)
      }
  }

  def getHistoryData() {
    import play.api.libs.ws.WS
    import play.api.libs.json._
    implicit val mtDataRead = Json.reads[MonitorTypeData]
    implicit val pipeRead = Json.reads[PipeData]
    import models.ModelHelper._

    def growData(isHead: Boolean) = {
      for {
        m <- Monitor.mvList
        monitor = Monitor.map(m)
        (plantID, pipeID) = monitor.getIDs
      } {
        val date = if (isHead)
          monitor.getHeadDate
        else
          monitor.getTailDate

        if (DateTime.now().toLocalDate() > date) {
          val dateStr = date.toString("YYYYMMdd")
          val url = s"http://cems.ilepb.gov.tw/OpenData/API/Daily/${plantID}/${pipeID}/${dateStr}/Json"
          val f = WS.url(url).get().map {
            response =>
              val result = response.json.validate[Seq[PipeData]]
              result.fold(
                error => {
                  Logger.error(JsError.toJson(error).toString())
                },
                pipeDataSeq => {
                  processData(m, date, pipeDataSeq, isHead)
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
    growData(false)
    //Grow head...
    growData(true)
  }

  def processData(m: Monitor.Value, day: LocalDate, pipeDataSeq: Seq[PipeData], isHead: Boolean) = {
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
    val recordMap = Map.empty[Monitor.Value, Map[DateTime, Map[MonitorType.Value, (Double, String)]]]

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

        mtMap.put(monitorType, (mtValue, status))
      }
    }

    val docs =
      for {
        monitorMap <- recordMap
        monitor = monitorMap._1
        timeMaps = monitorMap._2
        dateTime <- timeMaps.keys.toList.sorted
        mtMaps = timeMaps(dateTime) if !mtMaps.isEmpty
      } yield {
        Record.toDocument(monitor, dateTime, mtMaps.toList)
      }

    if (!docs.isEmpty) {
      val f = Record.insertRecords(docs.toSeq)(Record.MinCollection)
      f.onSuccess({
        case x =>
          val millis = day.toDateTimeAtStartOfDay().getMillis

          for (m <- recordMap.keys) {
            if (isHead)
              Monitor.updateHead(m, millis)
            else
              Monitor.updateTail(m, millis)

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