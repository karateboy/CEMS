package models
import play.api._

object Every8d {
  val account = Play.current.configuration.getString("every8d.account").get
  val password = Play.current.configuration.getString("every8d.password").get

  import com.every8d.ws._
  val service = {
    val ctrl = new com.every8d.ws.SMS
    ctrl.getSMSSoap
  }
  def sendSMS(subject:String, content:String, mobileList:List[String]) = {
    val sessionStr = service.getConnection(account, password)
    val sessionXML = xml.XML.loadString(sessionStr)
    sessionXML match {
      case <SMS>{ connections @ _* }</SMS> =>
        for (conn @ <GET_CONNECTION>{ _* }</GET_CONNECTION> <- connections) {
          val code = conn \ "CODE"
          val desp = conn \ "DESCRIPTION"
          if (code.text.toInt == 0) {
            val session = conn \ "SESSION_KEY"
            val mobile = mobileList.mkString(",")
            val xmlTag = service.sendSMS(session.text, subject, content, mobile, "")
            val ret = xmlTag.split(",")
            if(ret(0).toDouble >= 0)
              Logger.info(s"success send SMS to $mobile")
            else
              Logger.error(s"failed to send SMS")
              
            service.closeConnection(session.text)
          }else{
            Logger.error(s"${code.text}:${desp.text}")
          }
        }
    }

  }

}