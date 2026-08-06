package cargotracker.auth.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import play.api.mvc.*
import play.api.mvc.Results.Redirect

import java.time.{Clock, Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** 認証済みユーザーのみアクセス可能なリクエスト。 */
final case class AuthenticatedRequest[A](
    username: String,
    roles: Set[Role],
    request: Request[A]
) extends WrappedRequest[A](request):
  def hasRole(role: Role): Boolean = roles.contains(role)

/** 認証チェック付き ActionBuilder。
  *
  *   - セッションに `username` がなければ `/login` にリダイレクト
  *   - セッションの `lastAccessedAt` が 30 分超過していたら破棄してリダイレクト
  *   - 通過時は `lastAccessedAt` を現在時刻で更新する
  */
@Singleton
class AuthenticatedAction @Inject() (
    defaultParser: BodyParsers.Default,
    clock: Clock
)(using ec: ExecutionContext)
    extends ActionBuilder[AuthenticatedRequest, AnyContent]:

  override def parser: BodyParser[AnyContent] = defaultParser
  override protected def executionContext: ExecutionContext = ec

  private val SessionTimeout = Duration.ofMinutes(30)

  override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] =
    val session = request.session
    val now = Instant.now(clock)

    (session.get("username"), session.get("lastAccessedAt")) match
      case (Some(username), Some(lastStr)) =>
        val last = Instant.parse(lastStr)
        if Duration.between(last, now).compareTo(SessionTimeout) > 0 then
          Future.successful(Redirect("/login").withNewSession)
        else
          val roles = session
            .get("roles")
            .toSet
            .flatMap(_.split(",").toSet)
            .flatMap(Role.fromName)
          block(AuthenticatedRequest(username, roles, request))
            .map(_.addingToSession("lastAccessedAt" -> now.toString)(request))
      case _ =>
        Future.successful(Redirect("/login"))
