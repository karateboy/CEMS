package models
import com.github.nscala_time.time.Imports._
import scala.language.implicitConversions
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api._
import scala.collection.JavaConversions._


/**
 * @author user
 */

object ModelHelper {
  implicit def getSqlTimestamp(t: DateTime) = {
    new java.sql.Timestamp(t.getMillis)
  }

  implicit def getDateTime(st: java.sql.Timestamp) = {
    new DateTime(st)
  }

  import org.mongodb.scala.bson.BsonDateTime
  implicit def toDateTime(time: BsonDateTime) = new DateTime(time.getValue)
  implicit def toBsonDateTime(jdtime: DateTime) = new BsonDateTime(jdtime.getMillis)

  def main(args: Array[String]) {
    val timestamp = DateTime.parse("2015-04-01")
    println(timestamp.toString())
  }

  def logException(ex: Throwable) = {
    Logger.error(ex.getMessage, ex)
  }

  def errorHandler: PartialFunction[Throwable, Any] = {
    case ex: Throwable =>
      Logger.error("Error=>", ex)
      throw ex
  }

  def errorHandler(prompt: String = "Error=>"): PartialFunction[Throwable, Any] = {
    case ex: Throwable =>
      Logger.error(prompt, ex)
      throw ex
  }

  import scala.concurrent._

  def waitReadyResult[T](f: Future[T]) = {
    import scala.concurrent.duration._
    import scala.util._

    val ret = Await.ready(f, Duration.Inf).value.get

    ret match {
      case Success(t) =>
        t
      case Failure(ex) =>
        Logger.error(ex.getMessage, ex)
        throw ex
    }
  }
  
    import org.mongodb.scala.bson._
  def getOptionTime(key: String)(implicit doc:Document) = {
    if (doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(doc(key).asInt64().getValue)
  }
  
  def getOptionStr(key: String)(implicit doc:Document) = {
    if(doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(doc.getString(key))
  }

  def getOptionDouble(key: String)(implicit doc:Document) = {
    if (doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(doc(key).asDouble().getValue)
  }

  def getOptionInt(key: String)(implicit doc:Document) = {
    if (doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(doc(key).asInt32().getValue)
  }
  
  def getOptionLong(key: String)(implicit doc:Document) = {
    if (doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(doc(key).asInt64().getValue)
  }
    
  def getOptionDoc(key: String)(implicit doc:Document) = {
    if (doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(doc(key).asDocument())
  }
  
  def getArray[T](key: String, mapper:(BsonValue)=>T)(implicit doc:Document) = {

    val array = doc(key).asArray().getValues
    
    val result = array map {
        v => mapper(v)
      }
    result.toSeq
  }
  
  def getOptionArray[T](key:String, mapper:(BsonValue)=>T)(implicit doc:Document) = {
    if (doc.get(key).isEmpty || doc(key).isNull())
      None
    else
      Some(getArray(key, mapper))    
  }
  
}

object EnumUtils {
  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
        }
      }
      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }
}
