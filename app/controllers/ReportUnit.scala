package controllers
case class ReportUnit(id:String, name:String)
object ReportUnit extends Enumeration {
  val SixMin = Value
  val FifteenMin = Value
  val Hour = Value
  val Day = Value
  val Month = Value
  val Quarter = Value
  val Year = Value
  def mkPair(p:(ReportUnit.Value, String)) = 
    p._1 -> ReportUnit(p._1.toString, p._2)
 
  val listPair = List((SixMin->"6分"), (FifteenMin->"15分"), (Hour -> "小時"), (Day -> "日"), (Month -> "月"), (Quarter -> "季"), (Year -> "年"))
  val map = listPair.map(mkPair).toMap
}