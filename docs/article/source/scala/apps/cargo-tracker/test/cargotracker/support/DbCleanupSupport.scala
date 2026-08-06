package cargotracker.support

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterEach, Suite}
import scalikejdbc.*

/** 各テストの前にアプリケーション側テーブルを TRUNCATE して独立性を担保する（IT2 タスク 0.6）。
  *
  *   - `PostgresContainerSupport` のスイート全体共有コンテナと組み合わせる
  *   - コンテナ起動直後に Flyway マイグレーションを実行し、ScalikeJDBC ConnectionPool を登録する
  *   - `users` / `user_roles` は admin シード（[[cargotracker.auth.infrastructure.services.AdminUserSeeder]]）を温存するため除外
  *   - `flyway_schema_history` は Flyway の管理対象で触らない
  *   - `RESTART IDENTITY CASCADE` で BIGSERIAL を 1 に戻し、FK 連鎖を許容する
  *
  * 使い方:
  * {{{
  *   class FooSpec extends AnyFunSuite with Matchers
  *     with PostgresContainerSupport with DbCleanupSupport:
  *     ...
  * }}}
  */
trait DbCleanupSupport extends BeforeAndAfterEach:
  self: Suite & PostgresContainerSupport =>

  /** TRUNCATE 対象のテーブル。サブクラスが追加する場合はオーバーライドする。 */
  protected def cleanupTables: Seq[String] = Seq(
    "carrier_movement",
    "voyage",
    "route_candidate",
    "estimate",
    "cargo",
    "shipper"
  )

  override def afterContainersStart(containers: PostgreSQLContainer): Unit =
    Flyway
      .configure()
      .dataSource(containers.jdbcUrl, containers.username, containers.password)
      .locations("classpath:db/migration/default")
      .load()
      .migrate()
    ConnectionPool.singleton(containers.jdbcUrl, containers.username, containers.password)

  override protected def beforeEach(): Unit =
    super.beforeEach()
    if cleanupTables.nonEmpty then
      DB.localTx { implicit session =>
        val joined = cleanupTables.mkString(", ")
        SQL(s"TRUNCATE TABLE $joined RESTART IDENTITY CASCADE").update.apply()
      }
