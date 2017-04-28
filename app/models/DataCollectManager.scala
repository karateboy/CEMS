package models
import play.api._
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import com.github.nscala_time.time.Imports._
import play.api.Play.current
import Alarm._
import ModelHelper._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.ExecutionContext.Implicits.global

object DataCollectManager {
  val effectivRatio = 0.75
  case class MonitorTypeData(mt: MonitorType.Value, value: Double, status: String)
  case class ReportData(dataList: List[MonitorTypeData])
  case object CalculateData

  var manager: ActorRef = _
  def startup() = {
    manager = Akka.system.actorOf(Props[DataCollectManager], name = "dataCollectManager")
  }


  case object GetLatestData
  def getLatestData() = {
    import akka.pattern.ask
    import akka.util.Timeout
    import scala.concurrent.duration._
    implicit val timeout = Timeout(Duration(3, SECONDS))

    val f = manager ? GetLatestData
    f.mapTo[Map[MonitorType.Value, Record]]
  }

  import scala.collection.mutable.ListBuffer
  def calculateAvgMap(mtMap: Map[MonitorType.Value, Map[String, ListBuffer[Double]]]) = {
    for {
      mt <- mtMap.keys
      statusMap = mtMap(mt)
      total = statusMap.map { _._2.size }.sum if total != 0
    } yield {
      val minuteAvg =
        {
          val statusKV = {
            val kv = statusMap.maxBy(kv => kv._2.length)
            if (kv._1 == MonitorStatus.NormalStat &&
              statusMap(kv._1).size < statusMap.size * effectivRatio) {
              //return most status except normal
              val noNormalStatusMap = statusMap - kv._1
              noNormalStatusMap.maxBy(kv => kv._2.length)
            } else
              kv
          }
          val values = statusKV._2
          val avg = if (mt == MonitorType.WIN_DIRECTION) {
            val windDir = values
            val windSpeedStatusMap = mtMap.get(MonitorType.WIN_SPEED)
            import controllers.Query._
            if (windSpeedStatusMap.isDefined) {
              val windSpeedMostStatus = windSpeedStatusMap.get.maxBy(kv => kv._2.length)
              val windSpeed = windSpeedMostStatus._2
              windAvg(windSpeed.toList, windDir.toList)
            } else { //assume wind speed is all equal
              val windSpeed =
                for (r <- 1 to windDir.length)
                  yield 1.0
              windAvg(windSpeed.toList, windDir.toList)
            }
          } else {
            values.sum / values.length
          }
          (avg, statusKV._1)
        }
      mt -> minuteAvg
    }

  }

  def recalculateHourData(current: DateTime, forward: Boolean = true)(mtList: List[MonitorType.Value]) = {
    Logger.debug("calculate hour data " + (current - 1.hour))
  }

}

class DataCollectManager extends Actor {
  import DataCollectManager._

  val timer = {
    import scala.concurrent.duration._
    //Try to trigger at 30 sec
    val next30 = DateTime.now().withSecondOfMinute(30).plusMinutes(1)
    val postSeconds = new org.joda.time.Duration(DateTime.now, next30).getStandardSeconds
    Akka.system.scheduler.schedule(Duration(postSeconds, SECONDS), Duration(1, MINUTES), self, CalculateData)
  }

  var calibratorOpt: Option[ActorRef] = None

  case class InstrumentParam(actor: ActorRef, mtList: List[MonitorType.Value], calibrationTimerOpt: Option[Cancellable])

  def receive = handler(Map.empty[String, InstrumentParam], Map.empty[ActorRef, String],
    Map.empty[MonitorType.Value, Map[String, Record]], List.empty[(DateTime, String, List[MonitorTypeData])])

  def handler(instrumentMap: Map[String, InstrumentParam],
              collectorInstrumentMap: Map[ActorRef, String],
              latestDataMap: Map[MonitorType.Value, Map[String, Record]],
              mtDataList: List[(DateTime, String, List[MonitorTypeData])]): Receive = {
    case ReportData(dataList) =>
//      val now = DateTime.now
//
//      val instIdOpt = collectorInstrumentMap.get(sender)
//      instIdOpt map {
//        instId =>
//          val pairs =
//            for (data <- dataList) yield {
//              val currentMap = latestDataMap.getOrElse(data.mt, Map.empty[String, Record])
//              val filteredMap = currentMap.filter { kv =>
//                val r = kv._2
//                r.time >= DateTime.now() - 6.second
//              }
//
//              //(data.mt -> (filteredMap ++ Map(instId -> Record(now, data.value, data.status))))
//            }
//
//          context become handler(instrumentMap, collectorInstrumentMap,
//            latestDataMap ++ pairs, (DateTime.now, instId, dataList) :: mtDataList)
//      }

    case CalculateData => {
    }

    case GetLatestData =>
//      //Filter out older than 6 second
//      val latestMap = latestDataMap.flatMap { kv =>
//        val mt = kv._1
//        val instRecordMap = kv._2
//        val filteredRecordMap = instRecordMap.filter {
//          kv =>
//            val r = kv._2
//            r.time >= DateTime.now() - 6.second
//        }
//
//        val measuringList = MonitorType.map(mt).measuringBy.get
//        val instrumentIdOpt = measuringList.find { instrumentId => filteredRecordMap.contains(instrumentId) }
//        instrumentIdOpt map {
//          mt -> filteredRecordMap(_)
//        }
//      }
//
//      context become handler(instrumentMap, collectorInstrumentMap, latestDataMap, mtDataList)

      sender ! Map.empty[MonitorType.Value, Record]
  }

  override def postStop(): Unit = {
    timer.cancel()
  }

}