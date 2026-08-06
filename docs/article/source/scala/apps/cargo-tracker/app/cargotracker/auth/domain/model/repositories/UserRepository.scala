package cargotracker.auth.domain.model.repositories

import cargotracker.auth.domain.model.aggregates.User

/** ユーザー集約の永続化ポート。
  *
  *   - 実装は infrastructure 層で提供（ScalikeJDBC）
  *   - 認証時のメインクエリは [[findByUsername]]
  */
trait UserRepository:

  /** ユーザー名でユーザーを取得する。存在しなければ [[scala.None]]。 */
  def findByUsername(username: String): Option[User]

  /** ユーザーを保存する（新規登録 or 上書き）。 */
  def save(user: User): Unit
