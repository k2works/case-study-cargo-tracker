package cargotracker.auth.infrastructure.services

import cargotracker.auth.domain.model.aggregates.User
import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.domain.model.valueobjects.{PasswordHash, Role}
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import javax.inject.{Inject, Singleton}

/** アプリケーション起動時に開発用 admin ユーザーを投入する（IT1 レビュー H1 対応）。
  *
  *   - 資格情報は `application.conf` の `cargotracker.admin-seed` から読み込む（ハードコード禁止）
  *   - 環境変数 `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_SEED_ENABLED` で上書き可能
  *   - `enabled = false` で投入をスキップする（本番環境では false にする）
  *   - 既に同名ユーザーが存在する場合は何もしない（冪等）
  *   - すべてのロールを付与（マスタ管理 + 全業務操作可能）
  */
@Singleton
class AdminUserSeeder @Inject() (
    configuration: Configuration,
    userRepository: UserRepository,
    system: ActorSystem,
    ec: ExecutionContext
):

  private val seedConfig = configuration.get[Configuration]("cargotracker.admin-seed")
  private val enabled = seedConfig.get[Boolean]("enabled")
  private val adminUsername = seedConfig.get[String]("username")
  private val adminEmail = seedConfig.get[String]("email")
  private val adminPassword = seedConfig.get[String]("password")

  system.scheduler.scheduleOnce(5.seconds) {
    seed()
  }(ec)

  private def seed(): Unit =
    if enabled && userRepository.findByUsername(adminUsername).isEmpty then
      val candidate = for
        hash <- PasswordHash.fromPlain(adminPassword)
        user <- User.create(adminUsername, adminEmail, hash, Role.values.toSet)
      yield user
      candidate.foreach(userRepository.save)
