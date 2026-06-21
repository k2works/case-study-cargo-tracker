package cargotracker.auth.infrastructure.services

import cargotracker.auth.domain.model.aggregates.User
import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.domain.model.valueobjects.{PasswordHash, Role}

import javax.inject.{Inject, Singleton}

/** アプリケーション起動時に開発用 admin ユーザーを投入する。
  *
  *   - 既に同名ユーザーが存在する場合は何もしない（冪等）
  *   - `Module.scala` で `ScalikeJdbcInitializer` の後に `asEagerSingleton` 登録され、 ConnectionPool 初期化済みであることが保証される
  *   - 初期パスワード: `Adm1nPass!`（リリース前に強制変更が必要）
  *   - すべてのロールを付与（マスタ管理 + 全業務操作可能）
  */
@Singleton
class AdminUserSeeder @Inject() (userRepository: UserRepository):

  private val AdminUsername = "admin"
  private val AdminEmail = "admin@example.com"
  private val AdminPassword = "Adm1nPass!"

  seed()

  private def seed(): Unit =
    if userRepository.findByUsername(AdminUsername).isEmpty then
      val candidate = for
        hash <- PasswordHash.fromPlain(AdminPassword)
        user <- User.create(
          AdminUsername,
          AdminEmail,
          hash,
          Role.values.toSet
        )
      yield user
      candidate.foreach(userRepository.save)
