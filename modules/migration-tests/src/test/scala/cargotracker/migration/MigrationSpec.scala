package cargotracker.migration

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import java.sql.{Connection, DriverManager, SQLException}
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.scalatest.funsuite.AnyFunSuite
import org.testcontainers.utility.DockerImageName
import scala.util.Using

/** マイグレーションの検査（R-03・検査 I7・ADR-008）。PostgreSQL の実物で、テストごとに空の DB を作る。 */
class MigrationSpec extends AnyFunSuite with TestContainerForAll:

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:18"))

  private val counter = new AtomicInteger(0)

  /** テストごとに新しい空のデータベースを作り、その JDBC 接続情報を渡す。 */
  private def withFreshDb[A](test: (String, String, String) => A): A =
    withContainers { pg =>
      val name = s"t${counter.incrementAndGet()}"
      Using.resource(DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password)) { admin =>
        admin.createStatement().execute(s"CREATE DATABASE $name TEMPLATE template0")
      }
      test(pg.jdbcUrl.replaceFirst("/[^/?]+(\\?|$)", s"/$name$$1"), pg.username, pg.password)
    }

  private def platform(url: String, user: String, pw: String, target: Option[String] = None): Int =
    val c = Flyway
      .configure()
      .dataSource(url, user, pw)
      .locations("classpath:db/migration/platform")
      .schemas("platform", "reference")
      .defaultSchema("platform")
    target.foreach(t => c.target(MigrationVersion.fromVersion(t)))
    val f = c.load()
    val n = f.migrate().migrationsExecuted
    f.validate()
    n

  private def booking(url: String, user: String, pw: String): Int =
    val f = Flyway
      .configure()
      .dataSource(url, user, pw)
      .locations("classpath:db/migration/booking")
      .schemas("booking")
      .defaultSchema("booking")
      .load()
    val n = f.migrate().migrationsExecuted
    f.validate()
    n

  private def migrateAll(url: String, user: String, pw: String): Unit =
    platform(url, user, pw); booking(url, user, pw); ()

  private def conn[A](url: String, user: String, pw: String)(f: Connection => A): A =
    Using.resource(DriverManager.getConnection(url, user, pw))(f)

  /** 整合性制約の違反（SQLState 23xxx：CHECK・外部キー・一意・NOT NULL）で拒まれたときだけ true。 SQL
    * の書き間違いなど別のエラーは「拒否」と数えず、そのまま失敗させる（偽の緑を防ぐ）。
    */
  private def rejected(c: Connection, sql: String): Boolean =
    try { c.createStatement().execute(sql); false }
    catch case e: SQLException if e.getSQLState != null && e.getSQLState.startsWith("23") => true

  // 検査に使う ID（ULID）。形式を満たす固定値。
  private val U1 = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
  private val U2 = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
  private val U3 = "01ARZ3NDEKTSV4RRFFQ69G5FAX"

  test("I7: 空の DB に、共有 → booking の順で全マイグレーションを適用できる") {
    withFreshDb { (url, u, p) =>
      assert(platform(url, u, p) == 4)
      assert(booking(url, u, p) == 1)
    }
  }

  test("I7: 前の版まで適用済みの DB（データ入り）に、次の版を適用できる。再適用は何もしない") {
    withFreshDb { (url, u, p) =>
      assert(platform(url, u, p, Some("2")) == 2) // V3 の前の版
      conn(url, u, p)(
        _.createStatement().execute(
          "INSERT INTO reference.port VALUES ('AUSYD','AU','シドニー','Sydney')"
        )
      )
      assert(platform(url, u, p) == 2) // 残りの V3・V4 だけ
      assert(booking(url, u, p) == 1)
      assert(platform(url, u, p) == 0 && booking(url, u, p) == 0)
      conn(url, u, p) { c =>
        val rs = c
          .createStatement()
          .executeQuery("SELECT count(*) FROM reference.port WHERE port_code = 'AUSYD'")
        rs.next(); assert(rs.getInt(1) == 1)
      }
    }
  }

  test("R-09: 港湾は実在する UN/LOCODE の部分集合で、形式のドメイン型を満たす") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        val rs = c
          .createStatement()
          .executeQuery("SELECT port_code FROM reference.port ORDER BY port_code")
        val codes = Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1).trim).toList
        assert(codes == List("CNSHA", "DEHAM", "JPTYO", "KRPUS", "NLRTM", "SGSIN", "USLAX"))
      }
    }
  }

  test("ドメイン型: ULID と UN/LOCODE の形式を DB でも守る") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        assert(rejected(c, "SELECT 'not-a-ulid'::platform.ulid"))
        assert(rejected(c, "SELECT '81ARZ3NDEKTSV4RRFFQ69G5FAV'::platform.ulid")) // 先頭が 7 を超える
        assert(rejected(c, "SELECT 'JP1YO'::platform.port_code")) // 場所に数字 1 は使えない
        assert(!rejected(c, "SELECT 'JPTYO'::platform.port_code"))
      }
    }
  }

  private def insertShipper(
      c: Connection,
      id: String,
      email: String = "taro@example.com",
      tpe: String = "INDIVIDUAL"
  ) =
    c.createStatement()
      .execute(
        s"""INSERT INTO booking.shipper (shipper_id, shipper_type, name, address, contact, email, email_normalized,
         |  created_at, created_by, updated_at, updated_by)
         |VALUES ('$id', '$tpe', '山田', '東京', '03', '$email', lower('$email'), now(), '$U3', now(), '$U3')""".stripMargin
      )

  test("US02-AC02: 同じメールアドレスの荷主を登録できる（メールは一意制約にしない）") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        insertShipper(c, U1)
        insertShipper(c, U2)
        val rs = c
          .createStatement()
          .executeQuery(
            "SELECT count(*) FROM booking.shipper WHERE email_normalized = 'taro@example.com'"
          )
        rs.next(); assert(rs.getInt(1) == 2)
      }
    }
  }

  test("荷主: Bolt 1 は個人のみ（法人は Bolt 13）。メールの形式が不正な行は入らない") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        assert(
          rejected(
            c,
            "INSERT INTO booking.shipper (shipper_id, shipper_type, name, address, contact, email, email_normalized, created_at, created_by, updated_at, updated_by) " +
              s"VALUES ('$U1','CORPORATE','社','住所','03','a@b.com','a@b.com', now(),'$U3',now(),'$U3')"
          )
        )
        assert(
          rejected(
            c,
            "INSERT INTO booking.shipper (shipper_id, shipper_type, name, address, contact, email, email_normalized, created_at, created_by, updated_at, updated_by) " +
              s"VALUES ('$U1','INDIVIDUAL','名','住所','03','bad','bad', now(),'$U3',now(),'$U3')"
          )
        )
      }
    }
  }

  private def bookingRow(
      id: String,
      number: String,
      shipper: String,
      origin: String = "JPTYO",
      dest: String = "NLRTM",
      weight: String = "120.5",
      handover: String = "2026-12-01",
      deadline: String = "2027-01-15",
      status: String = "RECEIVED"
  ) =
    s"""INSERT INTO booking.booking_view (booking_id, booking_number, shipper_id, cargo_kind, item_name, weight_kg,
       |  length_cm, width_cm, height_cm, quantity, origin_port, destination_port, preferred_handover_date, deadline,
       |  status, available_commands, aggregate_version, last_event_id, created_at, updated_at)
       |VALUES ('$id', '$number', '$shipper', 'GENERAL', '精密機器', $weight, 100, 80, 60, 3, '$origin', '$dest',
       |  '$handover', '$deadline', '$status', ARRAY[]::text[], 1, '$U3', now(), now())""".stripMargin

  test("予約: 正しい行は入り、B11 の違反・存在しない港・不正な状態は DB でも拒む") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        insertShipper(c, U1)
        assert(!rejected(c, bookingRow(U2, "BK-000001", U1)))
        assert(rejected(c, bookingRow(U3, "BK-000002", U1, origin = "JPTYO", dest = "JPTYO")))
        assert(rejected(c, bookingRow(U3, "BK-000003", U1, weight = "0")))
        assert(rejected(c, bookingRow(U3, "BK-000004", U1, handover = "2027-02-01")))
        assert(rejected(c, bookingRow(U3, "BK-000005", U1, dest = "ZZZZZ")))
        assert(rejected(c, bookingRow(U3, "BK-000006", U1, status = "PENDING")))
        assert(rejected(c, bookingRow(U3, "BK-000001", U1))) // 予約番号は一意
      }
    }
  }

  test("履歴は追記専用でイベント ID が主キー：同じイベントを 2 回入れても行が増えない（冪等な再投影）") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        insertShipper(c, U1)
        c.createStatement().execute(bookingRow(U2, "BK-000001", U1))
        val insert =
          s"INSERT INTO booking.booking_history (event_id, booking_id, occurred_at, kind) VALUES ('$U3','$U2', now(), 'REGISTERED')"
        c.createStatement().execute(insert)
        assert(rejected(c, insert))
        assert(
          rejected(
            c,
            s"INSERT INTO booking.booking_history (event_id, booking_id, occurred_at, kind) VALUES ('${U1}','$U2', now(), 'UNKNOWN')"
          )
        )
      }
    }
  }

  test("冪等性キー: 同じ利用者の同じキーは 1 行だけ") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        val insert =
          s"INSERT INTO platform.idempotency_key VALUES ('$U1','k1','RegisterBooking','h','{}'::jsonb, now(), now() + interval '7 days')"
        c.createStatement().execute(insert)
        assert(rejected(c, insert))
        assert(!rejected(c, insert.replace("'k1'", "'k2'")))
      }
    }
  }

  test("予約番号の採番: シーケンスが 1 から単調に増える") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        def next(): Long = {
          val rs =
            c.createStatement().executeQuery("SELECT nextval('platform.booking_number_seq')");
          rs.next(); rs.getLong(1)
        }
        assert(List(next(), next(), next()) == List(1L, 2L, 3L))
      }
    }
  }

  test("Unit の独立: スキーマをまたぐ外部キーは reference への参照だけ") {
    withFreshDb { (url, u, p) =>
      migrateAll(url, u, p)
      conn(url, u, p) { c =>
        val rs = c
          .createStatement()
          .executeQuery(
            """SELECT tc.table_schema, ccu.table_schema
            |FROM information_schema.table_constraints tc
            |JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
            |WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema <> ccu.table_schema""".stripMargin
          )
        val crossings = Iterator
          .continually(rs)
          .takeWhile(_.next())
          .map(r => (r.getString(1), r.getString(2)))
          .toSet
        assert(crossings.forall(_._2 == "reference"), s"reference 以外への外部キー: $crossings")
        assert(crossings == Set(("booking", "reference"))) // 実際に参照している
      }
    }
  }
