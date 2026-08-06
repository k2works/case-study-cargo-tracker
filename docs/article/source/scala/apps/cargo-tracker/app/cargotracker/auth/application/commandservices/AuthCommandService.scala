package cargotracker.auth.application.commandservices

import cargotracker.auth.domain.model.aggregates.User
import cargotracker.auth.domain.model.repositories.UserRepository

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}

/** 認証コマンドサービス。
  *
  * ユーザー資格情報の検証と認証セッション情報の組み立てを担う。 永続化（UserRepository）への参照と時刻取得（Clock）を保持し、 コントローラは結果を受け取ってセッション Cookie の発行のみを行う。
  */
@Singleton
class AuthCommandService @Inject() (
    userRepository: UserRepository,
    clock: Clock
):

  /** ユーザー名 / パスワードで認証する。
    *
    * @return
    *   成功時は認証セッション情報、失敗時は `AuthError`
    */
  def authenticate(
      username: String,
      password: String
  ): Either[AuthCommandService.AuthError, AuthCommandService.AuthenticatedSession] =
    userRepository.findByUsername(username) match
      case Some(user) if user.authenticate(password) =>
        Right(AuthCommandService.AuthenticatedSession.fromUser(user, Instant.now(clock)))
      case _ =>
        Left(AuthCommandService.AuthError.InvalidCredentials)

object AuthCommandService:

  enum AuthError:
    case InvalidCredentials

  final case class AuthenticatedSession(
      username: String,
      rolesCsv: String,
      lastAccessedAt: Instant
  )

  object AuthenticatedSession:
    def fromUser(user: User, now: Instant): AuthenticatedSession =
      AuthenticatedSession(
        username = user.username,
        rolesCsv = user.roles.map(_.toString).mkString(","),
        lastAccessedAt = now
      )
