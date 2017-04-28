package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Play.current
import com.github.nscala_time.time.Imports._
import models._
import PdfUtility._
import scala.concurrent.ExecutionContext.Implicits.global
import models.ModelHelper._

object PeriodReport extends Enumeration {
  val DailyReport = Value("daily")
  val MonthlyReport = Value("monthly")
  val MinMonthlyReport = Value("MinMonthly")
  val YearlyReport = Value("yearly")
  def map = Map(DailyReport -> "日報", MonthlyReport -> "月報",
    MinMonthlyReport -> "分鐘月報",
    YearlyReport -> "年報")

}

object Report extends Controller {

  def monitorReport() = Security.Authenticated { implicit request =>
    Ok(views.html.monitorReport(""))
  }

  def getMinMonthlySocket = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      MinMonthlyReportWorker.props(out)
  }

  def getOverallStatMap(statMap: Map[MonitorType.Value, Map[DateTime, Stat]]) = {
    val mTypes = statMap.keys.toList
    statMap.map { pair =>
      val mt = pair._1
      val dateMap = pair._2
      val values = dateMap.values.toList
      val total = values.size
      val hour_total = values.map { _.total }.sum
      val count = values.count(_.isEffective)
      val hour_count = values.map { _.count }.sum
      val overCount = values.map { _.overCount }.sum
      val max = values.map { _.avg }.max
      val validValues = values.flatMap { _.avg }
      val min = if (validValues.isEmpty)
        None
      else
        Some(validValues.min)

      val avg =
        if (mt != MonitorType.WIN_DIRECTION) {
          if (total == 0 || count == 0)
            None
          else {
            Some(values.filter { _.avg.isDefined }.map { s => s.avg.get * s.total }.sum / (values.map(_.total).sum))
          }
        } else {
          val winSpeedMap = statMap(MonitorType.WIN_SPEED)
          val dates = dateMap.keys.toList
          val windDir = dates.map { dateMap }
          val windSpeed = dates.map { winSpeedMap }
          def windAvg(): Option[Double] = {
            val windRecord = windSpeed.zip(windDir).filter(w => w._1.avg.isDefined && w._2.avg.isDefined)
            if (windRecord.length == 0)
              None
            else {
              val wind_sin = windRecord.map {
                v => v._1.avg.get * Math.sin(Math.toRadians(v._2.avg.get))
              }.sum

              val wind_cos = windRecord.map(v => v._1.avg.get * Math.cos(Math.toRadians(v._2.avg.get))).sum
              Some(Query.windAvg(wind_sin, wind_cos))
            }
          }
          windAvg()
        }

      mt -> Stat(
        avg = avg,
        min = min,
        max = max,
        total = total,
        count = count,
        overCount = overCount,
        hour_count = Some(hour_count),
        hour_total = Some(hour_total))
    }
  }

  def getMonitorReport(monitorStr: String, reportTypeStr: String, startDate: Long, outputTypeStr: String) = Security.Authenticated {
    implicit request =>
      val monitor = Monitor.withName(java.net.URLDecoder.decode(monitorStr, "UTF-8"))
      val reportType = PeriodReport.withName(reportTypeStr)
      val start = new DateTime(startDate)
      val outputType = OutputType.withName(outputTypeStr)

      if (outputType == OutputType.html || outputType == OutputType.pdf) {
        val (title, output) =
          reportType match {
            case PeriodReport.DailyReport =>
              val adjustedStart = start.withMillisOfDay(0)
              val periodMap = Record.getRecordMap(Record.HourCollection)(MonitorType.mtvList, monitor, adjustedStart, adjustedStart + 1.day)
              val mtTimeMap = periodMap.map { pair =>
                val k = pair._1
                val v = pair._2
                k -> Map(v.map { r => r.time -> r }: _*)
              }
              val statMap = Query.getPeriodStatReportMap(periodMap, 1.day)(adjustedStart, adjustedStart + 1.day)

              ("日報", views.html.dailyReport(monitor, start, MonitorType.activeMtvList, mtTimeMap, statMap))

            case PeriodReport.MonthlyReport =>
              val adjustedStart = start.withDayOfMonth(1).withMillisOfDay(0)
              val periodMap = Record.getRecordMap(Record.HourCollection)(MonitorType.activeMtvList, monitor, adjustedStart, adjustedStart + 1.month)
              val statMap = Query.getPeriodStatReportMap(periodMap, 1.day)(adjustedStart, adjustedStart + 1.month)
              val overallStatMap = getOverallStatMap(statMap)
              ("月報", views.html.monthlyReport(monitor, adjustedStart, MonitorType.activeMtvList, statMap, overallStatMap))

            case PeriodReport.YearlyReport =>
              val adjustedStart = start.withDayOfYear(1).withMillisOfDay(0)
              val periodMap = Record.getRecordMap(Record.HourCollection)(MonitorType.activeMtvList, monitor, adjustedStart, adjustedStart + 1.year)
              val statMap = Query.getPeriodStatReportMap(periodMap, 1.month)(adjustedStart, adjustedStart + 1.year)
              val overallStatMap = getOverallStatMap(statMap)
              ("年報", views.html.yearlyReport(monitor, adjustedStart, MonitorType.activeMtvList, statMap, overallStatMap))

          }

        outputType match {
          case OutputType.html =>
            Ok(output)
          case OutputType.pdf =>
            Ok.sendFile(creatPdfWithReportHeader(title, output),
              fileName = _ =>
                play.utils.UriEncoding.encodePathSegment(title + start.toString("YYYYMM") + ".pdf", "UTF-8"))
        }
      } else {
        import java.io.File
        import java.nio.file.Files
        val (title, excelFile) =
          reportType match {
            case PeriodReport.DailyReport =>
              val adjustedStart = start.withMillisOfDay(0)
              ("日報" + start.toString("YYYYMMdd"), ExcelUtility.createDailyReport(monitor, adjustedStart))

            case PeriodReport.MonthlyReport =>
              val adjustedStart = start.withDayOfMonth(1).withMillisOfDay(0)
              ("月報" + adjustedStart.toString("YYYYMM"), ExcelUtility.createMonthlyReport(monitor, adjustedStart))

          }

        Ok.sendFile(excelFile, fileName = _ =>
          play.utils.UriEncoding.encodePathSegment(title + ".xlsx", "UTF-8"),
          onClose = () => { Files.deleteIfExists(excelFile.toPath()) })
      }
  }

  def monitorMonthlyHourReport = Security.Authenticated {
    Ok(views.html.monitorMonthlyHourReport())
  }

  def monthlyHourReport(monitorStr: String, monitorTypeStr: String, startDate: Long, outputTypeStr: String) = Security.Authenticated {
    val monitor = Monitor.withName(java.net.URLDecoder.decode(monitorStr, "UTF-8"))
    val mt = MonitorType.withName(java.net.URLDecoder.decode(monitorTypeStr, "UTF-8"))
    val start = {
      val original = new DateTime(startDate)
      original.withDayOfMonth(1).withMillisOfDay(0)
    }
    val outputType = OutputType.withName(outputTypeStr)
    val title = "月份時報表"
    if (outputType == OutputType.html || outputType == OutputType.pdf) {
      val recordList = Record.getRecordMap(Record.HourCollection)(List(mt), monitor, start, start + 1.month)(mt)
      val timePair = recordList.map { r => r.time -> r }
      val timeMap = Map(timePair: _*)

      def getHourPeriodStat(records: Seq[Record], hourList: List[DateTime]) = {
        if (records.length == 0)
          Stat(None, None, None, 0, 0, 0)
        else {
          val values = records.map { r => r.value }
          val min = values.min
          val max = values.max
          val sum = values.sum
          val count = records.filter { r => MonitorStatus.isValid(r.status) }.length
          val total = new Duration(start, start + 1.month).getStandardDays.toInt
          val overCount = if (MonitorType.map(mt).std_law.isDefined) {
            values.count { _ > MonitorType.map(mt).std_law.get }
          } else
            0

          val avg = if (mt == MonitorType.WIN_DIRECTION) {
            val windDir = records
            val windSpeed = hourList.map(timeMap)
            Query.windAvg(windSpeed, windDir)
          } else {
            sum / total
          }
          Stat(
            avg = Some(avg),
            min = Some(min),
            max = Some(max),
            total = total,
            count = count,
            overCount = overCount)
        }
      }

      val hourValues =
        for {
          h <- 0 to 23
          hourList = Query.getPeriods(start + h.hour, start + 1.month, 1.day)
        } yield {
          h -> getHourPeriodStat(hourList.flatMap { timeMap.get }, hourList)
        }
      val hourStatMap = Map(hourValues: _*)
      val dayStatMap = Query.getPeriodStatReportMap(Map(mt -> recordList), 1.day)(start, start + 1.month)
      val overallStat = Query.getPeriodStatReportMap(Map(mt -> recordList), 1.day)(start, start + 1.month)(mt)(start)
      Ok(views.html.monthlyHourReport(monitor, mt, start, timeMap, hourStatMap, dayStatMap(mt), overallStat))
    } else {
      import java.io.File
      import java.nio.file.Files
      Ok("")
    }
  }

  def audit = Security.Authenticated {
    Ok(views.html.audit())
  }

  def auditReport(monitorStr: String, monitorTypeStr: String, startLong: Long, endLong: Long) = Security.Authenticated.async {
    implicit request =>
      import scala.collection.JavaConverters._
      val monitor = Monitor.withName(java.net.URLDecoder.decode(monitorStr, "UTF-8"))

      val monitorTypeStrArray = monitorTypeStr.split(':')
      val monitorTypes = monitorTypeStrArray.map { MonitorType.withName }
      val (start, end) = (new DateTime(startLong), new DateTime(endLong))

      val recordMapF = Record.getAuditRecordMapFuture(Record.HourCollection)(monitorTypes.toList, monitor, start, end)
      for (recordMap <- recordMapF) yield {
        val timeList = recordMap.keys.toList.sorted

        val explain = monitorTypes.map { t =>
          val mtCase = MonitorType.map(t)
          s"${mtCase.desp}(${mtCase.unit})"
        }.mkString(",")
        val output = views.html.auditReport(monitorTypes, explain, start, end, timeList, recordMap)
        Ok(output)

      }
  }
  
  def reauditReport(monitorStr: String, monitorTypeStr: String, startLong: Long, endLong: Long) = Security.Authenticated.async {
    implicit request =>
      import scala.collection.JavaConverters._
      val monitor = Monitor.withName(java.net.URLDecoder.decode(monitorStr, "UTF-8"))

      val monitorTypeStrArray = monitorTypeStr.split(':')
      val monitorTypes = monitorTypeStrArray.map { MonitorType.withName }
      val (start, end) = (new DateTime(startLong), new DateTime(endLong))

      val f = Record.resetAuditedRecord(Record.HourCollection)(monitorTypes.toList, monitor, start, end)
      waitReadyResult(f)
      
      val recordListF = Record.getRecordListFuture(Record.HourCollection)(monitor, start, end)
      val recordList = waitReadyResult(recordListF)
      AutoAudit.audit2(monitor, recordList, false)
      
      val recordMapF = Record.getAuditRecordMapFuture(Record.HourCollection)(monitorTypes.toList, monitor, start, end)
      for (recordMap <- recordMapF) yield {
        val timeList = recordMap.keys.toList.sorted

        val explain = monitorTypes.map { t =>
          val mtCase = MonitorType.map(t)
          s"${mtCase.desp}(${mtCase.unit})"
        }.mkString(",")
        val output = views.html.auditReport(monitorTypes, explain, start, end, timeList, recordMap)
        Ok(output)

      }
  }

}