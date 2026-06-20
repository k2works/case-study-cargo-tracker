package cargotracker.auth.interfaces.web

import org.apache.pekko.stream.Materializer
import play.api.mvc.*
import play.api.mvc.Results.Redirect

import java.time.{Clock, Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// 全リクエストに認証チェックを適用する Play Filter。
//
//   - 公開 URL（login, logout, public/, health, assets/）は通過
//   - 保護リソースは username セッションが無い、または 30 分以上無操作ならログインにリダイレクトする
//   - 通過時は lastAccessedAt を現在時刻で更新する（スライディングタイムアウト）
@Singleton
class AuthFilter @Inject() (clock: Clock)(implicit
    val mat: Materializer,
    ec: ExecutionContext
) extends Filter:

  private val SessionTimeout = Duration.ofMinutes(30)

  private val publicPathPrefixes =
    Seq("/login", "/logout", "/public/", "/health", "/assets/")
  private val publicPaths = Set("/health")

  private def isPublic(path: String): Boolean =
    publicPaths.contains(path) || publicPathPrefixes.exists(path.startsWith)

  override def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] =
    if isPublic(requestHeader.path) then nextFilter(requestHeader)
    else
      val session = requestHeader.session
      val now = Instant.now(clock)

      (session.get("username"), session.get("lastAccessedAt")) match
        case (Some(_), Some(lastStr)) =>
          val last = Instant.parse(lastStr)
          if Duration.between(last, now).compareTo(SessionTimeout) > 0 then
            Future.successful(Redirect("/login").withNewSession)
          else
            nextFilter(requestHeader).map(
              _.addingToSession("lastAccessedAt" -> now.toString)(requestHeader)
            )
        case _ =>
          Future.successful(Redirect("/login"))
