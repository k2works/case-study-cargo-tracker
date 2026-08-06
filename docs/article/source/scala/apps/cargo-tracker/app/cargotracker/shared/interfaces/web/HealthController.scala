package cargotracker.shared.interfaces.web

import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import scalikejdbc.{DB, scalikejdbcSQLInterpolationImplicitDef}

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

/** ALB ターゲットグループ / Docker HEALTHCHECK 用エンドポイント。DB 疎通（SELECT 1）込みで判定する。 */
@Singleton
class HealthController @Inject() (cc: ControllerComponents) extends AbstractController(cc):

  def health(): Action[AnyContent] = Action {
    Try(DB.readOnly { implicit session => sql"SELECT 1".map(_.int(1)).single.apply() }) match
      case Success(_) => Ok(Json.obj("status" -> "UP"))
      case Failure(_) => ServiceUnavailable(Json.obj("status" -> "DOWN"))
  }
