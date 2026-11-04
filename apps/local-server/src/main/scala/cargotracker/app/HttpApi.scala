package cargotracker.app

import cargotracker.sharedkernel.Principal
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import scala.concurrent.{ExecutionContext, Future}

/** HTTP の入口。Mutation と Query は別のエンドポイント（BE-06）。ヘルスチェックは認証・制限の対象外（NFR-A14）。 */
final class HttpApi(
    devAuthEnabled: Boolean,
    command: (Principal, String, Json) => Future[Json],
    query: (Principal, String, Json) => Future[Json]
)(using ExecutionContext):

  private def json(status: StatusCodes.Success, body: Json): HttpResponse =
    HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`, body.noSpaces))

  private def error(
      status: org.apache.pekko.http.scaladsl.model.StatusCode,
      code: String,
      message: String
  ): HttpResponse =
    HttpResponse(
      status,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Json
          .obj(
            "errors" -> Json.arr(
              Json.obj(
                "message" -> Json.fromString(message),
                "extensions" -> Json.obj("code" -> Json.fromString(code))
              )
            )
          )
          .noSpaces
      )
    )

  /** 認証に失敗した理由は、認証されていない相手に教えない（存在や形式の違いを区別しない）。 */
  private def authenticated(inner: Principal => Route): Route =
    optionalHeaderValueByName("X-Debug-User-Id") { user =>
      optionalHeaderValueByName("X-Debug-Role") { role =>
        (if devAuthEnabled then DevAuth.principal(user, role) else None) match
          case Some(p) => inner(p)
          case None    => complete(error(StatusCodes.Unauthorized, "UNAUTHENTICATED", "認証が必要です"))
      }
    }

  private def graphql(run: (Principal, String, Json) => Future[Json])(principal: Principal): Route =
    entity(as[String]) { body =>
      parse(body).toOption
        .flatMap(_.asObject)
        .flatMap(o =>
          o("query")
            .flatMap(_.asString)
            .map(q => (q, o("variables").flatMap(_.asObject).getOrElse(JsonObject.empty)))
        ) match
        case None => complete(error(StatusCodes.BadRequest, "BAD_REQUEST", "JSON の本文に query が必要です"))
        case Some((q, vars)) =>
          onSuccess(run(principal, q, Json.fromJsonObject(vars)))(result =>
            complete(json(StatusCodes.OK, result))
          )
    }

  val routes: Route =
    concat(
      path("health")(
        get(complete(json(StatusCodes.OK, Json.obj("status" -> Json.fromString("UP")))))
      ),
      path("command" / "graphql")(post(authenticated(graphql(command)))),
      path("query" / "graphql")(post(authenticated(graphql(query))))
    )
