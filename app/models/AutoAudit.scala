package models
import play.api._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Record._
import MonitorType._
import com.github.nscala_time.time.Imports._
import models.ModelHelper._
import org.mongodb.scala.bson._

abstract class Rule(val lead: Char)

case class MinMaxCfg(
    id: MonitorType.Value,
    min: Double,
    max: Double) {
  def toDocument = Document("id" -> id, "min" -> min, "max" -> max)
}

case class MinMaxRule(
    enabled: Boolean,
    monitorTypes: Seq[MinMaxCfg]) extends Rule('a') {
  def toDocument = {
    val mts = monitorTypes map { _.toDocument }
    Document("enabled" -> enabled, "monitorTypes" -> mts)
  }

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true
    else {
      var pass = true
      for (cfg <- monitorTypes) {
        val mt = cfg.id

        if (mtMaps.contains(mt) && MonitorStatus.isValid(mtMaps(mt)._2)) {
          val mt_value = mtMaps(mt)._1
          val mt_status = mtMaps(mt)._2

          if (mt_value > cfg.max || mt_value <= cfg.min) {
            Record.updateRecordStatus(monitor, dateTime.getMillis, mt, lead + mt_status.substring(1))(Record.HourCollection)
            if (alarm)
              Alarm.log(monitor, mt,
                s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mt, Some(mt_value))} 違反極大極小值稽核")

            pass = false
          }
        }
      }
      pass
    }
  }
}

object MinMaxRule {
  implicit val minMaxCfgRead = Json.reads[MinMaxCfg]
  implicit val minMaxCfgWrite = Json.writes[MinMaxCfg]
  implicit val minMaxRuleWrite = Json.writes[MinMaxRule]
  implicit val minMaxRuleRead = Json.reads[MinMaxRule]

  val default = MinMaxRule(false, Seq())
  def toMinMaxCfg(implicit doc: Document) = {
    val id = doc.getString("id")
    val min = doc.getDouble("min")
    val max = doc.getDouble("max")
    MinMaxCfg(MonitorType.withName(id), min, max)
  }
  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")

    val mts = getArray("monitorTypes", d => toMinMaxCfg(d.asDocument()))

    MinMaxRule(enabled, mts)
  }
}

case class CompareRule(
    enabled: Boolean) extends Rule('b') {
  def toDocument = Document("enabled" -> enabled)

  //FIXME Nothing..
  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    true
  }
}

object CompareRule {
  implicit val compareRuleRead = Json.reads[CompareRule]
  implicit val compareRuleWrite = Json.writes[CompareRule]

  val default = CompareRule(false)
  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")

    CompareRule(enabled)
  }
}

case class DifferenceRule(
    enabled: Boolean,
    multiplier: Double,
    monitorTypes: Seq[MonitorType.Value]) extends Rule('c') {
  def toDocument = {
    Document("enabled" -> enabled, "multiplier" -> multiplier, "monitorTypes" -> monitorTypes)
  }

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val historyMap = Record.getRecordMap(Record.HourCollection)(monitorTypes.toList, monitor, dateTime - 24.hour, dateTime)
    val mtAvgStdPairs =
      for {
        mt <- monitorTypes if historyMap.contains(mt)
        mt_records = historyMap(mt).filter { rec => MonitorStatus.isValid(rec.status) }.map { _.value }
      } yield {
        val count = mt_records.length
        val avg = mt_records.sum / count
        val std = Math.sqrt(mt_records.map { r => (r - avg) * (r - avg) }.sum / count)
        mt -> (avg, std)
      }

    val avgStdMap = Map(mtAvgStdPairs: _*)

    for {
      mt <- monitorTypes if mtMaps.contains(mt)
      mr_record = mtMaps(mt) if (MonitorStatus.isValid(mr_record._2))
    } {
      val v = mr_record._1
      val (avg, std) = avgStdMap(mt)
      if (Math.abs(v - avg) > multiplier * std) {
        Record.updateRecordStatus(monitor, dateTime.getMillis, mt, lead + mr_record._2.substring(1))(Record.HourCollection)
        if (alarm)
          Alarm.log(monitor, mt,
            s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mt, Some(v))} 違反單調性稽核")
        pass = false
      }
    }

    pass
  }
}

object DifferenceRule {
  implicit val differenceRuleRead = Json.reads[DifferenceRule]
  implicit val differenceRuleWrite = Json.writes[DifferenceRule]

  val default = DifferenceRule(false, 3, Seq())

  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val multiplier = doc.getDouble("multiplier")
    val mts = getArray("monitorTypes", d => MonitorType.withName(d.asString().getValue))

    DifferenceRule(enabled, multiplier, mts)
  }

}

case class SpikeCfg(
    id: MonitorType.Value,
    abs: Double) {
  def toDocument = Document("id" -> id, "abs" -> abs)
}
case class SpikeRule(
    enabled: Boolean,
    monitorTypes: Seq[SpikeCfg]) extends Rule('d') {
  def toDocument = {
    val mts = monitorTypes map { _.toDocument }
    Document("enabled" -> enabled, "monitorTypes" -> mts)
  }

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val monitorTypeSeq = monitorTypes map { _.id }
    val historyMap = Record.getRecordMap(Record.HourCollection)(monitorTypeSeq.toList, monitor, dateTime - 2.hour, dateTime)

    for (mtcfg <- monitorTypes if mtMaps.contains(mtcfg.id) && historyMap.contains(mtcfg.id)) {
      val pre_records = historyMap(mtcfg.id)

      val mt_rec = mtMaps(mtcfg.id)
      val pre_mt_rec = pre_records.filter(r => MonitorStatus.isValid(r.status))
      if (MonitorStatus.isValid(mt_rec._2) && pre_mt_rec.length == 2) {
        val avg = (pre_mt_rec(0).value + mt_rec._1) / 2
        val v = pre_mt_rec(1).value
        if (Math.abs(v - avg) > mtcfg.abs) {
          pass = false
          Record.updateRecordStatus(monitor, dateTime.getMillis, mtcfg.id, lead + mt_rec._2.substring(1))(Record.HourCollection)
          if (alarm)
            Alarm.log(monitor, mtcfg.id,
              s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mtcfg.id, Some(v))} 突波高值稽核")
        }
      }
    }

    pass
  }

}

object SpikeRule {
  implicit val spikeCfgRead = Json.reads[SpikeCfg]
  implicit val spikeRuleRead = Json.reads[SpikeRule]
  implicit val spikeCfgWrite = Json.writes[SpikeCfg]
  implicit val spikeRuleWrite = Json.writes[SpikeRule]

  val default = SpikeRule(false, Seq())
  def toSpikeCfg(implicit doc: Document) = {
    val id = doc.getString("id")
    val abs = doc.getDouble("abs")
    SpikeCfg(MonitorType.withName(id), abs)
  }
  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val mts = getArray("monitorTypes", d => toSpikeCfg(d.asDocument()))
    SpikeRule(enabled, mts)
  }

}

case class PersistenceRule(
    enabled: Boolean,
    same: Int) extends Rule('e') {
  def toDocument = Document("enabled" -> enabled, "same" -> same)

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val historyMap = Record.getRecordMap(Record.HourCollection)(MonitorType.mtvList, monitor, dateTime - (same - 1).hour, dateTime)

    for (mt <- MonitorType.mtvList if mtMaps.contains(mt) && historyMap.contains(mt)) {
      val mt_rec = mtMaps(mt)
      if (MonitorStatus.isValid(mt_rec._2)) {
        val pre_mt_rec = historyMap(mt).filter(r => MonitorStatus.isValid(r.status)).filter(r => r.value == mt_rec._1)
        if (pre_mt_rec.length == same - 1) {
          pass = false
          Record.updateRecordStatus(monitor, dateTime.getMillis, mt, lead + mt_rec._2.substring(1))(Record.HourCollection)
          if (alarm)
            Alarm.log(monitor, mt,
              s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mt, Some(mt_rec._1))} 違反持續性稽核")
        }
      }
    }

    pass
  }
}

object PersistenceRule {
  implicit val persistenceRuleRead = Json.reads[PersistenceRule]
  implicit val persistenceRuleWrite = Json.writes[PersistenceRule]

  val default = PersistenceRule(false, 3)
  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val same = doc.getInteger("same")
    PersistenceRule(enabled, same)
  }
}

case class MonoCfg(
    id: MonitorType.Value,
    abs: Double) {
  def toDocument = Document("id" -> id, "abs" -> abs)
}

case class MonoRule(enabled: Boolean, count: Int,
                    monitorTypes: Seq[MonoCfg]) extends Rule('f') {

  def toDocument = {
    val mts = monitorTypes map { _.toDocument }
    Document("enabled" -> enabled, "count" -> count, "monitorTypes" -> mts)
  }

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val historyMap = Record.getRecordMap(Record.HourCollection)(MonitorType.mtvList, monitor, dateTime - (count - 1).hour, dateTime)

    for (mtcfg <- monitorTypes if historyMap.contains(mtcfg.id) && mtMaps.contains(mtcfg.id)) {
      val mt_rec = mtMaps(mtcfg.id)
      val pre_rec = historyMap(mtcfg.id).filter(r => MonitorStatus.isValid(r.status))
      if (MonitorStatus.isValid(mt_rec._2) && pre_rec.length == count - 1) {
        val values = pre_rec.map(_.value) ++ List(mt_rec._1)
        val max = values.max
        val min = values.min
        if ((max - min) < mtcfg.abs) {
          pass = false
          Record.updateRecordStatus(monitor, dateTime.getMillis, mtcfg.id, lead + mt_rec._2.substring(1))(Record.HourCollection)
          if (alarm)
            Alarm.log(monitor, mtcfg.id,
              s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mtcfg.id, Some(mt_rec._1))} 違反一致姓稽核")
        }
      }
    }

    pass
  }
}

object MonoRule {
  implicit val monoCfgRead = Json.reads[MonoCfg]
  implicit val monoCfgWrite = Json.writes[MonoCfg]
  implicit val monoRuleRead = Json.reads[MonoRule]
  implicit val monoRuleWrite = Json.writes[MonoRule]

  val default = MonoRule(false, 3, Seq.empty[MonoCfg])
  def toMonoCfg(implicit doc: Document) = {
    val id = doc.getString("id")
    val abs = doc.getDouble("abs")
    MonoCfg(MonitorType.withName(id), abs)
  }
  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val count = doc.getInteger("count")
    val mts = getArray("monitorTypes", d => toMonoCfg(d.asDocument()))
    MonoRule(enabled, count, mts)
  }
}

case class TwoHourRule(enabled: Boolean, monitorTypes: Seq[MonoCfg]) extends Rule('g') {
  def toDocument = {
    val mts = monitorTypes map { _.toDocument }
    Document("enabled" -> enabled, "monitorTypes" -> mts)
  }
  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val historyMap = Record.getRecordMap(Record.HourCollection)(MonitorType.mtvList, monitor, dateTime - 1.hour, dateTime)

    for (mtcfg <- monitorTypes if mtMaps.contains(mtcfg.id) && historyMap.contains(mtcfg.id)) {
      val mt = mtcfg.id
      val mt_rec = mtMaps(mt)
      val pre_rec = historyMap(mt).filter(r => MonitorStatus.isValid(r.status))
      if (MonitorStatus.isValid(mt_rec._2) && pre_rec.length == 1) {
        if (Math.abs(pre_rec(0).value - mt_rec._1) > mtcfg.abs) {
          pass = false
          Record.updateRecordStatus(monitor, dateTime.getMillis, mtcfg.id, lead + mt_rec._2.substring(1))(Record.HourCollection)
          if (alarm)
            Alarm.log(monitor, mtcfg.id,
              s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mtcfg.id, Some(mt_rec._1))} 違反小時值變換驗證")
        }
      }
    }

    pass
  }
}

object TwoHourRule {
  import MonoRule._
  implicit val read = Json.reads[TwoHourRule]
  implicit val write = Json.writes[TwoHourRule]
  val default = TwoHourRule(false, Seq.empty[MonoCfg])

  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val mts = getArray("monitorTypes", d => MonoRule.toMonoCfg(d.asDocument()))
    TwoHourRule(enabled, mts)
  }
}

case class ThreeHourCfg(
    id: MonitorType.Value,
    abs: Double,
    percent: Double) {
  def toDocument = Document("id" -> id, "abs" -> abs, "percent" -> percent)
}

case class ThreeHourRule(enabled: Boolean, monitorTypes: Seq[ThreeHourCfg]) extends Rule('h') {
  def toDocument = {
    val mts = monitorTypes.map { _.toDocument }
    Document("enabled" -> enabled, "monitorTypes" -> mts)
  }

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val historyMap = Record.getRecordMap(Record.HourCollection)(MonitorType.mtvList, monitor, dateTime - 2.hour, dateTime)

    for (mtcfg <- monitorTypes if mtMaps.contains(mtcfg.id) && historyMap.contains(mtcfg.id)) {
      val mt = mtcfg.id
      val mt_rec = mtMaps(mt)
      val pre_rec = historyMap(mt).filter(r => MonitorStatus.isValid(r.status))
      if (MonitorStatus.isValid(mt_rec._2) && pre_rec.length == 2) {
        val values = pre_rec.map(_.value) ++ List(mt_rec._1)
        val abs_percent =
          for (v1 <- values.zipWithIndex.dropRight(1)) yield {
            val v2 = values(v1._2)
            (Math.abs(v1._1 - v2), Math.abs((1 - v1._1 / v2) * 100))
          }
        val overs = abs_percent.filter(v => v._1 > mtcfg.abs && v._2 > mtcfg.percent)
        if (overs.length == 2) {
          pass = false
          Record.updateRecordStatus(monitor, dateTime.getMillis, mtcfg.id, lead + mt_rec._2.substring(1))(Record.HourCollection)
          if (alarm)
            Alarm.log(monitor, mtcfg.id,
              s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mtcfg.id, Some(mt_rec._1))} 違反三小時值變換驗證")
        }
      }
    }

    pass
  }
}

object ThreeHourRule {
  implicit val thcfgRead = Json.reads[ThreeHourCfg]
  implicit val thcfgWrite = Json.writes[ThreeHourCfg]
  implicit val reads = Json.reads[ThreeHourRule]
  implicit val writes = Json.writes[ThreeHourRule]

  val default = ThreeHourRule(false, Seq.empty[ThreeHourCfg])
  def toThreeHourCfg(implicit doc: Document) = {
    val id = doc.getString("id")
    val abs = doc.getDouble("abs")
    val percent = doc.getDouble("percent")
    ThreeHourCfg(MonitorType.withName(id), abs, percent)
  }
  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val mts = getArray("monitorTypes", d => toThreeHourCfg(d.asDocument()))
    ThreeHourRule(enabled, mts)
  }
}

case class FourHourCfg(
    id: MonitorType.Value,
    abs: Double) {
  def toDocument = Document("id" -> id, "abs" -> abs)
}

case class FourHourRule(enabled: Boolean, monitorTypes: Seq[FourHourCfg]) extends Rule('i') {
  def toDocument = {
    val mts = monitorTypes.map { _.toDocument }
    Document("enabled" -> enabled, "monitorTypes" -> mts)
  }

  def check(monitor: Monitor.Value, dateTime: DateTime, mtMaps: Map[MonitorType.Value, (Double, String)], alarm: Boolean) = {
    if (!enabled)
      true

    var pass = true
    val historyMap = Record.getRecordMap(Record.HourCollection)(MonitorType.mtvList, monitor, dateTime - 3.hour, dateTime)

    for (mtcfg <- monitorTypes if mtMaps.contains(mtcfg.id) && historyMap.contains(mtcfg.id)) {
      val mt = mtcfg.id
      val mt_rec = mtMaps(mt)
      val pre_rec = historyMap(mt).filter(r => MonitorStatus.isValid(r.status))
      if (MonitorStatus.isValid(mt_rec._2) && pre_rec.length == 3) {
        val values = pre_rec.map(_.value) ++ List(mt_rec._1)
        val avg = values.sum / 4
        if (avg > mtcfg.abs) {
          pass = false
          Record.updateRecordStatus(monitor, dateTime.getMillis, mtcfg.id, lead + mt_rec._2.substring(1))(Record.HourCollection)
          if (alarm)
            Alarm.log(monitor, mtcfg.id,
              s"${dateTime.toString("YYYY-MM-dd HH:mm")}測值${MonitorType.format(mtcfg.id, Some(mt_rec._1))} 違反四小時值變換驗證")
        }
      }
    }

    pass
  }
}

object FourHourRule {
  implicit val thcfgRead = Json.reads[FourHourCfg]
  implicit val thcfgWrite = Json.writes[FourHourCfg]
  implicit val reads = Json.reads[FourHourRule]
  implicit val writes = Json.writes[FourHourRule]

  val default = FourHourRule(false, Seq.empty[FourHourCfg])
  def toFourHourCfg(doc: Document) = {
    val id = doc.getString("id")
    val abs = doc.getDouble("abs")
    FourHourCfg(MonitorType.withName(id), abs)
  }

  def toRule(implicit doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val monitorTypes = getArray("monitorTypes", d => toFourHourCfg(d.asDocument()))
    FourHourRule(enabled, monitorTypes)
  }
}

case class OverInternalStdMinRule(enabled: Boolean, threshold: Int) extends Rule('j') {
  def toDocument = Document("enabled" -> enabled, "threshold" -> threshold)

  def check(m: Monitor.Value): Boolean = {
    if (!enabled)
      true

    var pass = true
    val mCase = Monitor.map(m)

    val recordMap = Record.getRecordMap(Record.MinCollection)(MonitorType.mtvList, m, DateTime.now - 1.hour, DateTime.now)
    for {
      mt <- MonitorType.mtvList if recordMap.contains(mt) && MonitorType.map(mt).std_internal.isDefined
      std_internal = MonitorType.map(mt).std_internal.get
      mtRecords = recordMap(mt)
    } {
      val over = mtRecords.count(r =>
        MonitorStatus.isValid(r.status)
          && r.value > std_internal)

      if (over > threshold) {
        pass = false
        Alarm.log(m, mt, s"分鐘值超過內控")
      }

    }

    pass
  }

}

object OverInternalStdMinRule {
  implicit val reads = Json.reads[OverInternalStdMinRule]
  implicit val writes = Json.writes[OverInternalStdMinRule]
  val default = OverInternalStdMinRule(false, 20)
  def toRule(doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val threshold = doc.getInteger("threshold")
    OverInternalStdMinRule(enabled, threshold)
  }
}

case class DataReadyMinRule(enabled: Boolean, overdue: Int) extends Rule('k') {
  def toDocument = Document("enabled" -> enabled, "overdue" -> overdue)

  /*
  def checkInvalid(m: Monitor.Value): Boolean = {
    if (!enabled)
      return false

    val currentMinOpt = Realtime.getLatestMonitorRecordTime(TableType.Min, m)
    if (currentMinOpt.isDefined) {
      val duetime = DateTime.now() - overdue.minutes
      if (currentMinOpt.get.toDateTime < duetime) {
        for (mt <- Monitor.map(m).monitorTypes) {
          val ar = Alarm2(m, DateTime.now, Src(mt), Level.ERR, "分鐘值回傳超時")
          try {
            Alarm2.insertAlarm(ar)
          } catch {
            case ex: Exception =>
            // Skip duplicate alarm
          }
        }
        return true
      }
    }

    false
  }
  * 
  */
}

object DataReadyMinRule {
  implicit val reads = Json.reads[DataReadyMinRule]
  implicit val writes = Json.writes[DataReadyMinRule]
  val default = DataReadyMinRule(false, 10)
  def toRule(doc: Document) = {
    val enabled = doc.getBoolean("enabled")
    val overdue = doc.getInteger("overdue")
    DataReadyMinRule(enabled, overdue)
  }
}

case class AutoAudit(
    minMaxRule: MinMaxRule,
    compareRule: CompareRule,
    differenceRule: DifferenceRule,
    spikeRule: SpikeRule,
    persistenceRule: PersistenceRule,
    monoRule: MonoRule,
    twoHourRule: TwoHourRule,
    threeHourRule: ThreeHourRule,
    fourHourRule: FourHourRule,
    overInternalStdMinRule: OverInternalStdMinRule,
    dataReadyMinRule: DataReadyMinRule) {
  def toDocument =
    Document("minMaxRule" -> minMaxRule.toDocument,
      "compareRule" -> compareRule.toDocument,
      "differenceRule" -> differenceRule.toDocument,
      "spikeRule" -> spikeRule.toDocument,
      "persistenceRule" -> persistenceRule.toDocument,
      "monoRule" -> monoRule.toDocument,
      "twoHourRule" -> twoHourRule.toDocument,
      "threeHourRule" -> threeHourRule.toDocument,
      "fourHourRule" -> fourHourRule.toDocument,
      "overInternalStdMinRule" -> overInternalStdMinRule.toDocument,
      "dataReadyMinRule" -> dataReadyMinRule.toDocument)
}

/**
 * @author user
 */
object AutoAudit {
  implicit val autoAuditRead = Json.reads[AutoAudit]
  implicit val autoAuditWrite = Json.writes[AutoAudit]

  val default = AutoAudit(
    MinMaxRule.default,
    CompareRule.default,
    DifferenceRule.default,
    SpikeRule.default,
    PersistenceRule.default,
    MonoRule.default,
    TwoHourRule.default,
    ThreeHourRule.default,
    FourHourRule.default,
    OverInternalStdMinRule.default,
    DataReadyMinRule.default)

  val map = Map(
    'a' -> "極大極小值",
    'b' -> "合理性",
    'c' -> "單調性",
    'd' -> "突波高值",
    'e' -> "持續性",
    'f' -> "一致性",
    'g' -> "小時測值變化驗證",
    'h' -> "三小時變化測值驗證",
    'i' -> "四小時變化測值驗證",
    'j' -> "分鐘值超過內控",
    'k' -> "分鐘值回傳超時")

  implicit object TransformAutoAudit extends BsonTransformer[AutoAudit] {
    def apply(a: AutoAudit): BsonDocument = a.toDocument.toBsonDocument
  }

  def toAutoAudit(doc: Document) = {
    val minMaxRule = MinMaxRule.toRule(doc("minMaxRule").asDocument())
    val compareRule = CompareRule.toRule(doc("compareRule").asDocument())
    val differenceRule = DifferenceRule.toRule(doc("differenceRule").asDocument())
    val spikeRule = SpikeRule.toRule(doc("spikeRule").asDocument())
    val persistenceRule = PersistenceRule.toRule(doc("persistenceRule").asDocument())
    val monoRule = MonoRule.toRule(doc("monoRule").asDocument())
    val twoHourRule = TwoHourRule.toRule(doc("twoHourRule").asDocument())
    val threeHourRule = ThreeHourRule.toRule(doc("threeHourRule").asDocument())
    val fourHourRule = FourHourRule.toRule(doc("fourHourRule").asDocument())
    val overInternalStdMinRule = OverInternalStdMinRule.toRule(doc("overInternalStdMinRule").asDocument())
    val dataReadyMinRule = DataReadyMinRule.toRule(doc("dataReadyMinRule").asDocument())
    AutoAudit(minMaxRule, compareRule, differenceRule, spikeRule, persistenceRule, monoRule, twoHourRule,
      threeHourRule, fourHourRule, overInternalStdMinRule, dataReadyMinRule)
  }

  def audit(recordMap: scala.collection.mutable.Map[Monitor.Value, scala.collection.mutable.Map[DateTime, scala.collection.mutable.Map[MonitorType.Value, (Double, String)]]], alarm: Boolean) = {
    for {
      monitorMap <- recordMap if Monitor.map(monitorMap._1).autoAudit.isDefined
      monitor = monitorMap._1
      autoAudit = Monitor.map(monitor).autoAudit.get
      timeMaps = monitorMap._2
      dateTime <- timeMaps.keys.toList.sorted
      mtMaps = timeMaps(dateTime) if (!mtMaps.isEmpty)
    } {
      if (autoAudit.minMaxRule.enabled)
        autoAudit.minMaxRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.compareRule.enabled)
        autoAudit.compareRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.differenceRule.enabled)
        autoAudit.differenceRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.spikeRule.enabled)
        autoAudit.spikeRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.persistenceRule.enabled)
        autoAudit.persistenceRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.monoRule.enabled)
        autoAudit.monoRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.twoHourRule.enabled)
        autoAudit.twoHourRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.threeHourRule.enabled)
        autoAudit.threeHourRule.check(monitor, dateTime, mtMaps.toMap, alarm)

      if (autoAudit.fourHourRule.enabled)
        autoAudit.differenceRule.check(monitor, dateTime, mtMaps.toMap, alarm)
    }
  }

  def audit2(monitor:Monitor.Value, recordSeq: Seq[Record.RecordList], alarm:Boolean){
    for {
      record <- recordSeq
      time = new DateTime(record.time)
      autoAuditOpt = Monitor.map(monitor).autoAudit if autoAuditOpt.isDefined
      autoAudit = autoAuditOpt.get
    } {
      val mtPair = record.mtDataList map { mtRecord =>
        val mt = MonitorType.withName(mtRecord.mtName)
        mt -> (mtRecord.value, mtRecord.status)
      }
      val mtMap = mtPair.toMap
      
      if (autoAudit.minMaxRule.enabled)
        autoAudit.minMaxRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.compareRule.enabled)
        autoAudit.compareRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.differenceRule.enabled)
        autoAudit.differenceRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.spikeRule.enabled)
        autoAudit.spikeRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.persistenceRule.enabled)
        autoAudit.persistenceRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.monoRule.enabled)
        autoAudit.monoRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.twoHourRule.enabled)
        autoAudit.twoHourRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.threeHourRule.enabled)
        autoAudit.threeHourRule.check(monitor, time, mtMap, alarm)

      if (autoAudit.fourHourRule.enabled)
        autoAudit.differenceRule.check(monitor, time, mtMap, alarm)      
    }
  }
}