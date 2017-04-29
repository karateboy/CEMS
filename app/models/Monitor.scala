package models
import scala.collection.Map
import play.api.Logger
import EnumUtils._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models.ModelHelper._
import com.github.nscala_time.time.Imports._
import scala.concurrent.ExecutionContext.Implicits.global
import org.mongodb.scala.bson._
import models.ModelHelper._

case class Monitor(_id: String, indParkName: String, dp_no: String,
                   lat: Option[Double] = None, lng: Option[Double] = None,
                   autoAudit: Option[AutoAudit] = None,
                   head: Option[Long] = None, tail: Option[Long] = None,
                   minHead: Option[Long] = None, minTail: Option[Long] = None) {

  def toDocument = {
    import AutoAudit._
    Document("_id" -> _id, "indParkName" -> indParkName, "dp_no" -> dp_no,
      "lat" -> lat, "lng" -> lng, "autoAudit" -> autoAudit, "head" -> head, "tail" -> tail,
      "minHead" -> minHead, "minTail" -> minTail)
  }

  def getPlantID = {
    val ids = _id.split("#")
    ids(0)
  }

  def getIDs = {
    val ids = _id.split("#")
    (ids(0), ids(1))
  }

  def defaultTail =
    if (DateTime.now().getHourOfDay < 12)
      DateTime.yesterday().withMillisOfDay(0).getMillis
    else
      DateTime.now().withMillisOfDay(0).getMillis

  def defaultHead =
    if (DateTime.now().getHourOfDay < 12)
      DateTime.yesterday().withMillisOfDay(0).getMillis
    else
      DateTime.now().withMillisOfDay(0).getMillis

  def getTailDate = (new DateTime(tail.getOrElse(defaultTail)) - 1.day).toLocalDate()
  def getHeadDate = (new DateTime(head.getOrElse(defaultHead)) + 1.day).toLocalDate()

  def getMinTailDate = (new DateTime(minTail.getOrElse(DateTime.now().withMillisOfDay(0).getMillis)) - 1.day).toLocalDate()
  def getMinHeadDate = (new DateTime(minHead.getOrElse(DateTime.yesterday().withMillisOfDay(0).getMillis)) + 1.day).toLocalDate()

}

object Monitor extends Enumeration {
  implicit val monitorRead: Reads[Monitor.Value] = EnumUtils.enumReads(Monitor)
  implicit val monitorWrite: Writes[Monitor.Value] = EnumUtils.enumWrites
  implicit val autoAuditRead = Json.reads[AutoAudit]
  implicit val autoAuditWrite = Json.writes[AutoAudit]

  implicit val mWrite = Json.writes[Monitor]
  implicit val mRead = Json.reads[Monitor]

  import org.mongodb.scala.bson._
  import scala.concurrent._
  import scala.concurrent.duration._

  implicit object TransformMonitor extends BsonTransformer[Monitor.Value] {
    def apply(m: Monitor.Value): BsonString = new BsonString(m.toString)
  }
  val colName = "monitors"
  val collection = MongoDB.database.getCollection(colName)

  def monitorId(plantID: String, pipeID: String) = s"${plantID}#${pipeID}"

  val defaultMonitorList = Seq(
    Monitor(monitorId("G3200778", "P101"), "台灣水泥股份有限公司蘇澳廠", "#3旋窯"),
    Monitor(monitorId("G3200778", "P102"), "台灣水泥股份有限公司蘇澳廠", "#3冷卻機"),
    Monitor(monitorId("G3200778", "P202"), "台灣水泥股份有限公司蘇澳廠", "#4旋窯"),
    Monitor(monitorId("G3200778", "P301"), "台灣水泥股份有限公司蘇澳廠", "#6旋窯"),
    Monitor(monitorId("G3200778", "P302"), "台灣水泥股份有限公司蘇澳廠", "#6冷卻機"),

    Monitor(monitorId("G3200849", "P201"), "信大水泥股份有限公司南聖湖廠", "#2旋窯"),
    Monitor(monitorId("G3200849", "P203"), "信大水泥股份有限公司南聖湖廠", "#2冷卻機"),
    Monitor(monitorId("G3200849", "P301"), "信大水泥股份有限公司南聖湖廠", "#3旋窯"),
    Monitor(monitorId("G3200849", "P304"), "信大水泥股份有限公司南聖湖廠", "#3冷卻機"),

    Monitor(monitorId("G4100017", "P002"), "幸福水泥股份有限公司東澳廠", "#2旋窯"),
    Monitor(monitorId("G4100017", "P004"), "幸福水泥股份有限公司東澳廠", "#2冷卻機"),

    Monitor(monitorId("G3700791", "P105"), "臺灣化學纖維股份有限公司龍德廠", "LT2鍋爐"),
    Monitor(monitorId("G3700791", "P115"), "臺灣化學纖維股份有限公司龍德廠", "LT3鍋爐"),

    Monitor(monitorId("G37A0585", "P002"), "潤泰精密材料股份有限公司宜蘭冬山廠", "旋窯"),
    Monitor(monitorId("G37A0585", "P007"), "潤泰精密材料股份有限公司宜蘭冬山廠", "冷卻機"),

    Monitor(monitorId("G32A0540", "P001"), "羅東鋼鐵股份有限公司煉鋼廠", "電弧爐"),

    Monitor(monitorId("G3801239", "P001"), "宜蘭縣利澤資源回收(焚化)廠", "#1焚化爐"),
    Monitor(monitorId("G3801239", "P002"), "宜蘭縣利澤資源回收(焚化)廠", "#2焚化爐"))

  def init(colNames: Seq[String]) = {
    if (!colNames.contains(colName)) {
      val f = MongoDB.database.createCollection(colName).toFuture()
      f.onFailure(errorHandler)
      f.onSuccess({
        case _: Seq[t] =>
          defaultMonitorList map { newMonitor }
      })
      Some(f.mapTo[Unit])
    } else
      None
  }

  def toMonitor(implicit doc: Document) = {
    val _id = doc.getString("_id")
    val indParkName = doc.getString("indParkName")
    val dp_no = doc.getString("dp_no")
    val lat = getOptionDouble("lat")
    val lng = getOptionDouble("lng")
    val autoAudit = getOptionDoc("autoAudit") map { d => AutoAudit.toAutoAudit(d) }
    val head = getOptionLong("head")
    val tail = getOptionLong("tail")
    val minHead = getOptionLong("minHead")
    val minTail = getOptionLong("minTail")

    Monitor(_id = _id, indParkName = indParkName, dp_no = dp_no, lat = lat, lng = lng,
      autoAudit = autoAudit, head = head, tail = tail, minHead = minHead, minTail = minTail)
  }

  def newMonitor(m: Monitor) = {
    Logger.debug(s"Create monitor value ${m._id}!")
    val v = Value(m._id)
    map = map + (v -> m)
    mvList = (v :: mvList.reverse).reverse

    val f = collection.insertOne(m.toDocument).toFuture()
    f.onFailure(errorHandler)
    f.onSuccess({
      case _: Seq[t] =>
    })
    Monitor.withName(m._id)
  }

  private def mList: List[Monitor] =
    {
      val f = MongoDB.database.getCollection(colName).find().toFuture()
      val r = waitReadyResult(f)
      r.map { toMonitor(_) }.toList
    }

  def refreshMonitor = {
    val list = mList
    for (m <- list) {
      try {
        Monitor.withName(m._id)
      } catch {
        case _: NoSuchElementException =>
          map = map + (Value(m._id) -> m)
      }
    }
    mvList = list.map(m => Monitor.withName(m._id))

  }

  var map: Map[Value, Monitor] = Map(mList.map { e => Value(e._id) -> e }: _*)
  var mvList = mList.map(mt => Monitor.withName(mt._id))
  def indParkSet = mvList.map { map(_).indParkName }.foldRight(Set.empty[String])((name, set) => set + name)

  def plantIdSet = mvList.map { map(_).getPlantID }.foldRight(Set.empty[String])((name, set) => set + name)

  def getMonitorValueByName(indParkName: String, dp_no: String) = {
    val id = monitorId(indParkName, dp_no)
    Monitor.withName(id)
  }

  def format(v: Option[Double]) = {
    if (v.isEmpty)
      "-"
    else
      v.get.toString
  }

  def updateMonitor(m: Monitor.Value, colname: String, newValue: String) = {
    import org.mongodb.scala._
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._
    import org.mongodb.scala.model.FindOneAndUpdateOptions

    import scala.concurrent.ExecutionContext.Implicits.global
    Logger.debug(s"col=$colname newValue=$newValue")
    val idFilter = equal("_id", map(m)._id)
    val opt = FindOneAndUpdateOptions().returnDocument(com.mongodb.client.model.ReturnDocument.AFTER)
    val f =
      if (newValue == "-")
        collection.findOneAndUpdate(idFilter, set(colname, null), opt).toFuture()
      else {
        import java.lang.Double
        collection.findOneAndUpdate(idFilter, set(colname, Double.parseDouble(newValue)), opt).toFuture()
      }

    val ret = waitReadyResult(f)

    val mCase = toMonitor(ret(0))
    Logger.debug(mCase.toString)
    map = map + (m -> mCase)
  }

  def updateRecord(m: Monitor.Value, colname: String, newValue: Long) = {
    import org.mongodb.scala._
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._
    import org.mongodb.scala.model.FindOneAndUpdateOptions

    assert(colname == "head" || colname == "tail" || colname == "minHead" || colname == "minTail")

    val idFilter = equal("_id", map(m)._id)
    val opt = FindOneAndUpdateOptions().returnDocument(com.mongodb.client.model.ReturnDocument.AFTER)
    val f =
      collection.findOneAndUpdate(idFilter, set(colname, newValue), opt).toFuture()

    val ret = waitReadyResult(f)

    val mCase = toMonitor(ret(0))
    map = map + (m -> mCase)
  }

  def updateHead(m: Monitor.Value, newValue: Long) = updateRecord(m, "head", newValue)
  def updateTail(m: Monitor.Value, newValue: Long) = updateRecord(m, "tail", newValue)
  def updateMinHead(m: Monitor.Value, newValue: Long) = updateRecord(m, "minHead", newValue)
  def updateMinTail(m: Monitor.Value, newValue: Long) = updateRecord(m, "minTail", newValue)

  def updateMonitorAutoAudit(m: Monitor.Value, autoAudit: AutoAudit) = {
    import org.mongodb.scala._
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._
    import org.mongodb.scala.model.FindOneAndUpdateOptions

    import scala.concurrent.ExecutionContext.Implicits.global

    val idFilter = equal("_id", map(m)._id)
    val opt = FindOneAndUpdateOptions().returnDocument(com.mongodb.client.model.ReturnDocument.AFTER)
    val f = collection.findOneAndUpdate(idFilter, set("autoAudit", autoAudit.toDocument), opt).toFuture()

    val ret = waitReadyResult(f)

    val mCase = toMonitor(ret(0))
    map = map + (m -> mCase)
  }

  def getCenterLat(privilege: Privilege) = {
    val monitors = privilege.allowedMonitors.filter { m => privilege.allowedIndParks.contains(Monitor.map(m).indParkName) }
    val latList = monitors.flatMap { m => Monitor.map(m).lat }
    latList.sum / latList.length
  }

  def getCenterLng(privilege: Privilege) = {
    val monitors = privilege.allowedMonitors.filter { m => privilege.allowedIndParks.contains(Monitor.map(m).indParkName) }
    val lngList = monitors.flatMap { m => Monitor.map(m).lng }
    lngList.sum / lngList.length
  }
}