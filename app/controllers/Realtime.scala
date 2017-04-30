package controllers
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Play.current
import play.api.data._
import play.api.data.Forms._
import play.api.libs.ws._
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import com.github.nscala_time.time.Imports._
import Highchart._
import models._

object Realtime extends Controller {
  val overTimeLimit = 6
  case class MonitorTypeStatus(desp: String, value: String, unit: String, instrument: String, status: String, classStr: String, order: Int)
  def MonitorTypeStatusList() = Security.Authenticated.async {
    implicit request =>
      import MonitorType._

      implicit val mtsWrite = Json.writes[MonitorTypeStatus]

      val result =
        for (dataMap <- DataCollectManager.getLatestData()) yield {
          val list =
            for {
              mt <- MonitorType.realtimeMtvList
              recordOpt = dataMap.get(mt)
            } yield {
              val mCase = map(mt)

              if (recordOpt.isDefined) {
                val record = recordOpt.get
                val duration = new Duration(record.time, DateTime.now())
                val (overInternal, overLaw) = overStd(mt, record.value)
                val status = if (duration.getStandardSeconds <= overTimeLimit)
                  MonitorStatus.map(record.status).desp
                else
                  "通訊中斷"

                MonitorTypeStatus(mCase.desp, format(mt, Some(record.value)), mCase.unit, "",
                  MonitorStatus.map(record.status).desp,
                  MonitorStatus.getCssClassStr(record.status, overInternal, overLaw), mCase.order)
              } else {
                MonitorTypeStatus(mCase.desp, format(mt, None), mCase.unit, "",
                  "通訊中斷",
                  "abnormal_status", mCase.order)
              }
            }
          Ok(Json.toJson(list))
        }

      result
  }

  def realtimeStatus = Security.Authenticated {
    Ok(views.html.realtimeStatusA(""))
  }
  def realtimeStatusContent() = Security.Authenticated.async {
    implicit request =>
      import MonitorType._
      val user = request.user
      val userFuture = User.getUserByEmailFuture(user.id)
      val latestRecordMapFuture = Record.getLatestMtRecordMapFuture(Record.MinCollection)

      for {
        userOpt <- User.getUserByEmailFuture(user.id) if userOpt.isDefined
        groups <- Group.findGroup(userOpt.get.groupId)
        map <- latestRecordMapFuture
      } yield {
        if (groups.isEmpty)
          Ok(views.html.realtimeStatus(Map.empty[MonitorType.Value, (DateTime, Map[Monitor.Value, Record])], Privilege.emptyPrivilege))
        else {
          val group = groups(0)
          Ok(views.html.realtimeStatus(map, group.privilege))
        }
      }
  }

  case class MonitorInfo(id: String, status: Int, winDir: Double, winSpeed: Double, statusStr: String)
  case class RealtimeMapInfo(info: Seq[MonitorInfo])

  implicit val monitorInfoWrite = Json.writes[MonitorInfo]
  implicit val mapInfoWrite = Json.writes[RealtimeMapInfo]


  def highchartJson(monitorTypeStr: String) = Security.Authenticated.async {
    implicit request =>
      val userOptF = User.getUserByEmailFuture(request.user.id)
      val myMonitorListFuture = Privilege.myMonitorList(request.user.id)
      val mt = MonitorType.withName(monitorTypeStr)

      for {
        userOpt <- userOptF if userOpt.isDefined
        groupF = Group.findGroup(userOpt.get.groupId)
        groupSeq <- groupF
        myMonitors <- myMonitorListFuture
        latestDataTimeOpt <- Record.getLatestMonitorTypeRecordTimeFuture(Record.MinCollection, mt)
      } yield {
        val group = groupSeq(0)

        val latestRecordTime = latestDataTimeOpt.getOrElse(DateTime.now())

        val reportUnit = if (mt == MonitorType.withName("OP"))
          ReportUnit.SixMin
        else if(mt == MonitorType.withName("FLOW") || mt == MonitorType.withName("TEMP"))
          ReportUnit.Hour
        else
          ReportUnit.FifteenMin

        import controllers.Query.trendHelper
        val chart = trendHelper(myMonitors.toArray, Array(mt), TableType.min, reportUnit,
          latestRecordTime - 1.day, latestRecordTime)(MonitorStatusFilter.ValidData)
        Ok(Json.toJson(chart))

      }

  }
}