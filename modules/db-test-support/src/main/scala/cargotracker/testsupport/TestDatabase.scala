package cargotracker.testsupport

import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import scala.util.Using

/** DB を使うテストの共通部品（L5）。PostgreSQL の実物を JVM あたり 1 回だけ起動し、 呼び出しごとに**空の DB を作って、共有 → booking
  * の順に全マイグレーションを適用**する。 メモリ上の別 DB は使わない（ADR-011）。本番コードからは使わない。
  */
final case class TestDatabase(url: String, user: String, password: String)

object TestDatabase:

  private lazy val container: PostgreSQLContainer[?] =
    val c = new PostgreSQLContainer("postgres:18")
    c.start()
    sys.addShutdownHook(c.stop())
    c

  private val counter = new AtomicInteger(0)

  /** 空の DB を作り、全マイグレーションを適用して返す。 */
  def fresh(): TestDatabase =
    val name = s"it_${System.nanoTime()}_${counter.incrementAndGet()}"
    Using.resource(
      DriverManager
        .getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
    ) { admin =>
      admin.createStatement().execute(s"CREATE DATABASE $name TEMPLATE template0")
    }
    val url = container.getJdbcUrl.replaceFirst("/[^/?]+(\\?|$)", s"/$name$$1")
    val db = TestDatabase(url, container.getUsername, container.getPassword)
    migrate(db)
    db

  private def migrate(db: TestDatabase): Unit =
    Flyway
      .configure()
      .dataSource(db.url, db.user, db.password)
      .locations("classpath:db/migration/platform")
      .schemas("platform", "reference")
      .defaultSchema("platform")
      .load()
      .migrate()
    Flyway
      .configure()
      .dataSource(db.url, db.user, db.password)
      .locations("classpath:db/migration/booking")
      .schemas("booking")
      .defaultSchema("booking")
      .load()
      .migrate()
    ()
