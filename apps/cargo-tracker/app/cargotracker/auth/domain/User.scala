package cargotracker.auth.domain

/** 認証ユーザー集約ルート。
  *
  *   - ユーザー名・メール・ハッシュ済みパスワード・ロール集合・有効フラグを保持
  *   - 認証（[[authenticate]]）と無効化（[[disable]]）の業務操作を提供
  *   - 状態変更はイミュータブルに新インスタンスを返す
  */
final case class User private (
    username: String,
    email: String,
    password: PasswordHash,
    roles: Set[Role],
    enabled: Boolean
):

  /** 平文パスワードを照合して認証する。無効化されたユーザーは常に失敗する。 */
  def authenticate(rawPassword: String): Boolean =
    enabled && password.matches(rawPassword)

  /** 指定ロールを保持しているかを判定する。 */
  def hasRole(role: Role): Boolean = roles.contains(role)

  /** ユーザーを無効化する。 */
  def disable: User = copy(enabled = false)

object User:

  /** ユーザー生成時のドメインエラー */
  sealed trait Error
  case object InvalidUsername extends Error
  case object InvalidEmail extends Error
  case object RolesRequired extends Error

  private val EmailPattern =
    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".r

  def create(
      username: String,
      email: String,
      password: PasswordHash,
      roles: Set[Role]
  ): Either[Error, User] =
    for
      validUsername <- Option(username)
        .map(_.trim)
        .filter(_.nonEmpty)
        .toRight(InvalidUsername)
      validEmail <- Option(email)
        .filter(e => EmailPattern.findFirstIn(e).isDefined)
        .toRight(InvalidEmail)
      validRoles <- Some(roles).filter(_.nonEmpty).toRight(RolesRequired)
    yield User(validUsername, validEmail, password, validRoles, enabled = true)
