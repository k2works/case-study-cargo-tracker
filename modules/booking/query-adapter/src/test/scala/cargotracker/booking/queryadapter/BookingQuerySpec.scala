package cargotracker.booking.queryadapter

import cargotracker.sharedkernel.{Clock, Principal, Role, Ulid}
import cargotracker.testsupport.TestDatabase
import io.circe.Json
import io.circe.parser.parse
import java.time.Instant
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** 読み取り側（GraphQL の Query）。認可はサービスの入口で行い、業務エラーはエラーコードで返す（500 にしない。A4）。 */
class BookingQuerySpec extends AnyFunSuite:
  given ExecutionContext = ExecutionContext.global

  private val at = Instant.parse("2026-11-04T01:00:00Z")
  private val rnd = new Random(44)
  private def ulid() = Ulid.generate(Clock.fixed(at), rnd).value

  private def principal(role: Role) = Principal(Ulid.parse(ulid()).toOption.get, role)

  private final class Env:
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    val schema = new BookingGraphQL(
      new QueryService(new ShipperQueries(db), new BookingQueries(db))
    )
    val taro = ulid()
    val hanako = ulid()
    val bookingId = ulid()
    def run[A](a: DBIO[A]): A = Await.result(db.run(a), 5.seconds)
    private def shipper(id: String, name: String, email: String) =
      sqlu"""INSERT INTO booking.shipper (shipper_id, shipper_type, name, address, contact, email, email_normalized,
             created_at, created_by, updated_at, updated_by)
             VALUES ($id, 'INDIVIDUAL', $name, '東京都千代田区', '03-1234-5678', $email, ${email.toLowerCase}, now(), $taro, now(), $taro)"""
    run(shipper(taro, "山田 太郎", "taro@example.com"))
    run(shipper(hanako, "鈴木 花子", "hanako@example.com"))
    run(
      sqlu"""INSERT INTO booking.booking_view
               (booking_id, booking_number, shipper_id, cargo_kind, item_name, weight_kg, length_cm, width_cm, height_cm,
                quantity, origin_port, destination_port, preferred_handover_date, deadline, status,
                available_commands, aggregate_version, last_event_id, created_at, updated_at)
             VALUES ($bookingId, 'BK-000001', $taro, 'GENERAL', '精密機器', 120.5, 100, 80, 60, 3, 'JPTYO', 'NLRTM',
                '2026-12-01', '2027-01-15', 'RECEIVED', ARRAY[]::text[], 1, ${ulid()}, now(), now())"""
    )
    run(
      sqlu"""INSERT INTO booking.booking_history (event_id, booking_id, occurred_at, actor_user_id, kind)
             VALUES (${ulid()}, $bookingId, now(), $taro, 'REGISTERED')"""
    )

    def query(role: Role, q: String, variables: Json = Json.obj()): Json =
      Await.result(schema.execute(principal(role), q, variables), 5.seconds)

  private def code(json: Json): Option[String] =
    json.hcursor.downField("errors").downArray.downField("extensions").get[String]("code").toOption
  private def data(json: Json) = json.hcursor.downField("data")

  test("営業担当者は荷主を名前で検索できる（部分一致）") {
    val env = new Env
    val r = env.query(Role.Sales, """{ shippers(keyword: "山田") { id name email kind } }""")
    val names = data(r)
      .downField("shippers")
      .values
      .get
      .flatMap(_.hcursor.get[String]("name").toOption)
      .toList
    assert(names == List("山田 太郎"), r.noSpaces)
  }

  test("検索語が空なら全件（上限つき）を返す。% や _ は文字として扱う") {
    val env = new Env
    val all = env.query(Role.Sales, """{ shippers { id } }""")
    assert(data(all).downField("shippers").values.get.size == 2)
    val wild = env.query(Role.Sales, """{ shippers(keyword: "%") { id } }""")
    assert(data(wild).downField("shippers").values.get.isEmpty)
  }

  test("US02-AC02: 荷主の詳細を取れる") {
    val env = new Env
    val r =
      env.query(Role.Sales, s"""{ shipper(id: "${env.taro}") { id name address contact email } }""")
    assert(
      data(r).downField("shipper").get[String]("email") == Right("taro@example.com"),
      r.noSpaces
    )
  }

  test("認可（A8）: 荷主の情報（個人情報）を見られるのは営業担当者だけ。他の 5 ロールは FORBIDDEN") {
    val env = new Env
    Role.values.foreach { role =>
      val r = env.query(role, """{ shippers { id name } }""")
      if role == Role.Sales then assert(code(r).isEmpty, role.toString)
      else assert(code(r) == Some("FORBIDDEN") && data(r).focus.forall(_.isNull), role.toString)
    }
  }

  test("認可（A8）: 予約を見られるのは、営業担当者・経路設計者・追跡管理者") {
    val env = new Env
    val viewers = Set(Role.Sales, Role.RouteDesigner, Role.TrackingManager)
    Role.values.foreach { role =>
      val r = env.query(role, """{ bookings { id } }""")
      if viewers(role) then assert(code(r).isEmpty, role.toString)
      else assert(code(r) == Some("FORBIDDEN"), role.toString)
    }
  }

  test("認可は入力検証より先：権限の無いロールには、不正な ID でも FORBIDDEN を返す") {
    val env = new Env
    assert(code(env.query(Role.Accountant, """{ shipper(id: "x") { id } }""")) == Some("FORBIDDEN"))
  }

  test("SC-25: 予約一覧は状態と荷主で絞れる。版（version）と実行できる操作を含む") {
    val env = new Env
    val r = env.query(
      Role.Sales,
      s"""{ bookings(status: "RECEIVED", shipperId: "${env.taro}") { id number status version availableCommands } }"""
    )
    val first = data(r).downField("bookings").downArray
    assert(first.get[String]("number") == Right("BK-000001"))
    assert(
      first.get[String]("status") == Right("RECEIVED") && first.get[Long]("version") == Right(1L),
      r.noSpaces
    )
    val other = env.query(Role.Sales, s"""{ bookings(shipperId: "${env.hanako}") { id } }""")
    assert(data(other).downField("bookings").values.get.isEmpty)
  }

  test("SC-27: 予約の詳細に、貨物・輸送条件・履歴（実行者を含む）がある") {
    val env = new Env
    val r = env.query(
      Role.RouteDesigner,
      s"""{ booking(id: "${env.bookingId}") { number status version cargo { itemName weightKg quantity } origin destination deadline history { kind actorUserId } } }"""
    )
    val b = data(r).downField("booking")
    assert(b.downField("cargo").get[String]("itemName") == Right("精密機器"), r.noSpaces)
    assert(b.downField("history").downArray.get[String]("kind") == Right("REGISTERED"))
    assert(b.downField("history").downArray.get[String]("actorUserId") == Right(env.taro))
  }

  test("存在しない予約・荷主は NOT_FOUND。不正な ID や状態は INVALID_INPUT（例外や 500 にしない）") {
    val env = new Env
    assert(
      code(env.query(Role.Sales, s"""{ booking(id: "${ulid()}") { id } }""")) == Some("NOT_FOUND")
    )
    assert(
      code(env.query(Role.Sales, s"""{ shipper(id: "${ulid()}") { id } }""")) == Some("NOT_FOUND")
    )
    assert(
      code(env.query(Role.Sales, """{ booking(id: "not-a-ulid") { id } }""")) == Some(
        "INVALID_INPUT"
      )
    )
    assert(
      code(env.query(Role.Sales, """{ bookings(status: "PENDING") { id } }""")) == Some(
        "INVALID_INPUT"
      )
    )
    assert(
      code(env.query(Role.Sales, """{ bookings(shipperId: "x") { id } }""")) == Some(
        "INVALID_INPUT"
      )
    )
  }

  test("文法の誤りや存在しない項目は、GraphQL の検証エラーとして返る（サーバーエラーにしない）") {
    val env = new Env
    val r = env.query(Role.Sales, "{ nothing }")
    assert(r.hcursor.downField("errors").downArray.succeeded)
    val syntax = env.query(Role.Sales, "{ shippers ")
    assert(syntax.hcursor.downField("errors").downArray.succeeded)
  }
