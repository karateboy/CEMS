package models
import play.api._
import com.github.nscala_time.time.Imports._
import models.ModelHelper._
import models._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json
import scala.collection.JavaConversions._
import scala.language.implicitConversions

case class AlarmConfig(enable:Boolean)
object AlarmConfig {
  val defaultConfig = AlarmConfig(false)
  implicit val acRead = Json.reads[AlarmConfig]
  implicit val acWrite = Json.writes[AlarmConfig]
  
  import org.mongodb.scala.bson._
  implicit object TransformAlarmConfig extends BsonTransformer[AlarmConfig] {
    def apply(config: AlarmConfig): BsonDocument = {
      Document("enable"->config.enable).toBsonDocument
    }
  }
  implicit def toAlarmConfig(doc:BsonDocument)={
    if(doc.get("enable").isBoolean()){
      val enable = doc.get("enable").asBoolean().getValue
      //val bsonStatusFilter = doc.get("statusFilter").asArray().getValues
      //val statusFilter = bsonStatusFilter.map { x => x.asString().getValue }
      Some(AlarmConfig(enable))
    }else{
      None
    }
  }
}