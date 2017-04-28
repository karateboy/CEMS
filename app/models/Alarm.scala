package models

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

import org.mongodb.scala._

import com.github.nscala_time.time.Imports._

import ModelHelper._
import play.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._

//alarm src format: 'T':"MonitorType"
//                  'I':"Instrument"
//                  'S':"System"

object Alarm {
  case class Alarm(time: DateTime, monitor: Monitor.Value, monitorType: MonitorType.Value, desc: String)

  implicit val write = Json.writes[Alarm]
  //implicit val format = Json.format[Alarm]

  val collectionName = "alarms"
  val collection = MongoDB.database.getCollection(collectionName)
  def toDocument(ar: Alarm) = {
    import org.mongodb.scala.bson._
    Document("time" -> (ar.time: BsonDateTime), "monitor" -> ar.monitor.toString,
      "monitorType" -> ar.monitorType.toString, "desc" -> ar.desc)
  }

  def toAlarm(doc: Document) = {
    val time = new DateTime(doc.get("time").get.asDateTime().getValue)
    val monitor = Monitor.withName(doc.get("monitor").get.asString().getValue)
    val monitorType = MonitorType.withName(doc.get("monitorType").get.asString().getValue)
    val desc = doc.get("desc").get.asString().getValue
    Alarm(time, monitor, monitorType, desc)
  }

  def init(colNames: Seq[String]) {
    import org.mongodb.scala.model.Indexes._
    if (!colNames.contains(collectionName)) {
      val f = MongoDB.database.createCollection(collectionName).toFuture()
      f.onFailure(errorHandler)
      f.onSuccess({
        case _: Seq[_] =>
          collection.createIndex(ascending("time", "monitor", "monitorType"))
      })
    }
  }

  import org.mongodb.scala.model.Filters._
  import org.mongodb.scala.model.Projections._
  import org.mongodb.scala.model.Sorts._

  def getAlarms(monitorList: List[Monitor.Value], monitorTypeList: List[MonitorType.Value], start: DateTime, end: DateTime) = {
    import org.mongodb.scala.bson.BsonDateTime
    val startB: BsonDateTime = start
    val endB: BsonDateTime = end
    val monitorStrList = monitorList map { _.toString }
    val monitorTypeStrList = monitorTypeList map { _.toString }

    val f = collection.find(and(gte("time", startB), lt("time", endB),
      in("monitor", monitorStrList: _*), in("monitorType", monitorTypeStrList: _*))).sort(ascending("time")).toFuture()

    val docs = waitReadyResult(f)
    docs.map { toAlarm }
  }

  def getAlarmsFuture(monitorList: List[Monitor.Value], monitorTypeList: List[MonitorType.Value], start: DateTime, end: DateTime) = {
    import org.mongodb.scala.bson.BsonDateTime
    val startB: BsonDateTime = start
    val endB: BsonDateTime = end
    val monitorStrList = monitorList map { _.toString }
    val monitorTypeStrList = monitorTypeList map { _.toString }

    val f = collection.find(and(gte("time", startB), lt("time", endB),
      in("monitor", monitorStrList: _*), in("monitorType", monitorTypeStrList: _*))).sort(ascending("time")).toFuture()

    for (docs <- f)
      yield docs.map { toAlarm }
  }

  private def checkForDuplicatelog(ar: Alarm) {
    import org.mongodb.scala.bson.BsonDateTime
    //None blocking...
    val start: BsonDateTime = ar.time - 30.minutes
    val end: BsonDateTime = ar.time

    val countObserver = collection.count(and(gte("time", start), lt("time", end),
      equal("monitor", ar.monitor.toString), equal("monitorType", ar.monitorType.toString), equal("desc", ar.desc)))

    countObserver.subscribe(
      (count: Long) => {
        if (count == 0) {
          val f = collection.insertOne(toDocument(ar)).toFuture()
        }
      }, // onNext
      (ex: Throwable) => Logger.error("Alarm failed:", ex), // onError
      () => {} // onComplete
      )
  }

  def log(monitor: Monitor.Value, monitorType: MonitorType.Value, desc: String) {
    val ar = Alarm(DateTime.now(), monitor, monitorType, desc)
    collection.insertOne(toDocument(ar)).toFuture()
    val f = User.getAllUsersFuture()
    for (users <- f) {
      val phoneList =
        for (user <- users if user.alarmConfig.isDefined && user.alarmConfig.get.enable) 
          yield user.phone
      val msg = s"${Monitor.map(monitor).dp_no}:${MonitorType.map(monitorType).desp} $desc"
      Every8d.sendSMS("警報", msg, phoneList.toList)
    }
  }
}