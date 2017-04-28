package models
import play.api._
import play.api.mvc._
import play.api.Logger
import models.ModelHelper._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.language.implicitConversions

case class MenuRight(id: MenuRight.Value, desp: String)
object MenuRight extends Enumeration {
  implicit val mReads: Reads[MenuRight.Value] = EnumUtils.enumReads(MenuRight)
  implicit val mWrites: Writes[MenuRight.Value] = EnumUtils.enumWrites
  implicit val mrWrites = Json.writes[MenuRight]

  val RealtimeInfo = Value("RealtimeInfo")
  val DataQuery = Value("DataQuery")
  val Report = Value("Report")
  val SystemManagement = Value("SystemManagement")

  val map = Map(
    RealtimeInfo -> "即時資訊",
    DataQuery -> "數據查詢",
    Report -> "報表查詢",
    SystemManagement -> "系統管理")
  def getDisplayName(v: MenuRight.Value) = {
    map(v)
  }
}

case class Privilege(
  allowedIndParks: Seq[String],
  allowedMonitors: Seq[Monitor.Value],
  allowedMonitorTypes: Seq[MonitorType.Value],
  allowedMenuRights: Seq[MenuRight.Value])

import scala.concurrent.ExecutionContext.Implicits.global
object Privilege {
  implicit val privilegeWrite = Json.writes[Privilege]
  implicit val privilegeRead = Json.reads[Privilege]

  lazy val defaultPrivilege = Privilege(Monitor.indParkSet.toSeq, Monitor.values.toSeq, MonitorType.values.toSeq, MenuRight.values.toSeq)
  val emptyPrivilege = Privilege(Seq.empty[String], Seq.empty[Monitor.Value], Seq.empty[MonitorType.Value], Seq.empty[MenuRight.Value])

  def myMonitorList(email: String) = {
    val userOptF = User.getUserByEmailFuture(email)
    for {
      userOpt <- userOptF if userOpt.isDefined
      groupF = Group.findGroup(userOpt.get.groupId)
      groupSeq <- groupF
    } yield {
      if (groupSeq.length == 0)
        Seq.empty[Monitor.Value]
      else {
        val group = groupSeq(0)
        group.privilege.allowedMonitors.filter { m =>
          val indParkName = Monitor.map(m).indParkName
          group.privilege.allowedIndParks.contains(indParkName)
        }
      }
    }

  }
}