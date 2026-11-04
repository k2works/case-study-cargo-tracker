package cargotracker.app

import cargotracker.sharedkernel.{Clock, Ulid}
import cargotracker.testsupport.TestDatabase
import io.circe.Json
import io.circe.parser.parse
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Instant
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.Await
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** ローカルの結線の E2E（HTTP から DB まで）。コマンド → イベント → 投影 → Query が通る。 */
class LocalServerSpec extends AnyFunSuite with BeforeAndAfterAll:

  private given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "local-server-spec", LocalServer.actorSystemConfig)
  override def afterAll(): Unit = system.terminate()

  private val http = HttpClient.newHttpClient()
  private val sales =
    Ulid.generate(Clock.fixed(Instant.parse("2026-11-04T00:00:00Z")), new java.util.Random(3)).value

  private final class Env(devAuth: Boolean = true):
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    val server = Await.result(
      LocalServer
        .start(ServerConfig(testDb.url, testDb.user, testDb.password, "127.0.0.1", 0, devAuth), db),
      20.seconds
    )
    val base = s"http://127.0.0.1:${server.port}"
    def stop(): Unit = Await.result(server.stop(), 10.seconds)

    def post(
        path: String,
        body: Json,
        role: Option[String] = Some("Sales"),
        user: String = sales
    ): (Int, Json) =
      val b =
        HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
      role.foreach(r => b.header("X-Debug-Role", r).header("X-Debug-User-Id", user))
      val res = http.send(
        b.POST(HttpRequest.BodyPublishers.ofString(body.noSpaces)).build(),
        HttpResponse.BodyHandlers.ofString()
      )
      (res.statusCode(), parse(res.body()).getOrElse(Json.Null))
    def command(q: String, role: Option[String] = Some("Sales")) =
      post("/command/graphql", Json.obj("query" -> Json.fromString(q)), role)
    def query(q: String, role: Option[String] = Some("Sales")) =
      post("/query/graphql", Json.obj("query" -> Json.fromString(q)), role)

  private def eventually[A](what: String)(f: => Option[A]): A =
    val deadline = System.nanoTime() + 15.seconds.toNanos
    var r = f
    while r.isEmpty && System.nanoTime() < deadline do { Thread.sleep(100); r = f }
    r.getOrElse(fail(s"時間内に成立しなかった: $what"))

  test("ヘルスチェックは認証なしで 200（過負荷でも落とさない対象。NFR-A14）") {
    val env = new Env
    try
      val res = http.send(
        HttpRequest.newBuilder(URI.create(env.base + "/health")).GET().build(),
        HttpResponse.BodyHandlers.ofString()
      )
      assert(res.statusCode() == 200)
    finally env.stop()
  }

  test("認証（スタブ）: 開発用の認証が無効なら GraphQL は 401。有効でも、ヘッダーが無い・不正なら 401") {
    val off = new Env(devAuth = false)
    try assert(off.command("mutation { ping }", Some("Sales"))._1 == 401)
    finally off.stop()
    val on = new Env
    try
      assert(on.command("{ ping }", None)._1 == 401)
      assert(on.command("{ ping }", Some("Nobody"))._1 == 401)
      assert(
        on.post(
          "/command/graphql",
          Json.obj("query" -> Json.fromString("{ ping }")),
          Some("Sales"),
          user = "not-a-ulid"
        )._1 == 401
      )
      assert(on.command("{ ping }")._1 == 200)
    finally on.stop()
  }

  test("本文が JSON でない・query が無いリクエストは 400（サーバーエラーにしない）") {
    val env = new Env
    try
      val b = HttpRequest
        .newBuilder(URI.create(env.base + "/query/graphql"))
        .header("X-Debug-Role", "Sales")
        .header("X-Debug-User-Id", sales)
      val res = http.send(
        b.POST(HttpRequest.BodyPublishers.ofString("not json")).build(),
        HttpResponse.BodyHandlers.ofString()
      )
      assert(res.statusCode() == 400)
      assert(env.post("/query/graphql", Json.obj())._1 == 400)
    finally env.stop()
  }

  test("E2E: 荷主を登録 → 予約を登録 → 一覧に現れる（応答の版に追いつくまで待つ）。履歴に実行者が残る") {
    val env = new Env
    try
      val (_, s) = env.command(
        """mutation { registerShipper(idempotencyKey: "s1", name: "山田 太郎", address: "東京都", contact: "03-0000-0000", email: "taro@example.com") { shipperId } }"""
      )
      val shipperId = s.hcursor
        .downField("data")
        .downField("registerShipper")
        .get[String]("shipperId")
        .toOption
        .get
      val bookingId = Ulid
        .generate(Clock.fixed(Instant.parse("2026-11-04T00:00:00Z")), new java.util.Random(99))
        .value
      val (_, b) = env.command(
        s"""mutation { registerBooking(idempotencyKey: "b1", bookingId: "$bookingId", shipperId: "$shipperId", itemName: "精密機器",
           |  weightKg: "120.5", lengthCm: "100", widthCm: "80", heightCm: "60", quantity: 3, origin: "JPTYO", destination: "NLRTM",
           |  preferredHandover: "2026-12-01", deadline: "2027-01-15") { bookingId number version } }""".stripMargin
      )
      val reg = b.hcursor.downField("data").downField("registerBooking")
      val version = reg.get[Long]("version").toOption.get
      assert(reg.get[String]("number") == Right("BK-000001") && version == 1L, b.noSpaces)
      // 結果整合：応答の版以上になるまで待つ（画面と同じ作法。バックエンド 6.4）
      val seen = eventually("一覧に反映") {
        val (_, q) = env.query("""{ bookings { id number status version } }""")
        q.hcursor
          .downField("data")
          .downField("bookings")
          .values
          .flatMap(_.find(_.hcursor.get[String]("id") == Right(bookingId)))
          .filter(_.hcursor.get[Long]("version").exists(_ >= version))
      }
      assert(seen.hcursor.get[String]("status") == Right("RECEIVED"))
      val (_, d) = env.query(
        s"""{ booking(id: "$bookingId") { history { kind actorUserId } cargo { itemName } } }"""
      )
      val h = d.hcursor.downField("data").downField("booking")
      assert(
        h.downField("history").downArray.get[String]("actorUserId") == Right(sales),
        d.noSpaces
      )
      assert(h.downField("cargo").get[String]("itemName") == Right("精密機器"))
    finally env.stop()
  }

  test("受け付けたのに一覧に出ない予約を作らない：実在しない港は拒み、列に入る最大の値は一覧まで届く") {
    val env = new Env
    try
      val (_, s) = env.command(
        """mutation { registerShipper(idempotencyKey: "s1", name: "山田 太郎", address: "東京都", contact: "03-0000-0000", email: "taro@example.com") { shipperId } }"""
      )
      val shipperId =
        s.hcursor
          .downField("data")
          .downField("registerShipper")
          .get[String]("shipperId")
          .toOption
          .get
      def newId(seed: Long) = Ulid
        .generate(Clock.fixed(Instant.parse("2026-11-04T00:00:00Z")), new java.util.Random(seed))
        .value
      def register(key: String, id: String, origin: String, dest: String) =
        env
          .command(
            s"""mutation { registerBooking(idempotencyKey: "$key", bookingId: "$id", shipperId: "$shipperId", itemName: "品",
             |  weightKg: "999999999.999", lengthCm: "999999.99", widthCm: "999999.99", heightCm: "999999.99", quantity: 2147483647,
             |  origin: "$origin", destination: "$dest", preferredHandover: "2026-12-01", deadline: "2027-01-15") { bookingId number version } }""".stripMargin
          )
          ._2
      // 実在しない港（形式は正しい）
      val bad = register("bad", newId(1), "JPTYO", "USZZZ")
      val code =
        bad.hcursor.downField("errors").downArray.downField("extensions").get[String]("code")
      assert(code == Right("VALIDATION_FAILED"), bad.noSpaces)
      assert(bad.noSpaces.contains("destination"), bad.noSpaces)
      // 列に入る最大の値は、一覧に現れる（投影が失敗して待避されない）
      val ok = register("ok", newId(2), "JPTYO", "NLRTM")
      assert(
        ok.hcursor.downField("data").downField("registerBooking").get[String]("number").isRight,
        ok.noSpaces
      )
      eventually("最大値の予約が一覧に反映") {
        val (_, q) = env.query("""{ bookings { id number } }""")
        Option.when(q.noSpaces.contains("BK-000001"))(())
      }
    finally env.stop()
  }

  test("冪等: 同じキーの再送で、予約は 1 件のまま（二重に登録しない）") {
    val env = new Env
    try
      val (_, s) = env.command(
        """mutation { registerShipper(idempotencyKey: "s1", name: "山田", address: "東京都", contact: "03", email: "taro@example.com") { shipperId } }"""
      )
      val shipperId = s.hcursor
        .downField("data")
        .downField("registerShipper")
        .get[String]("shipperId")
        .toOption
        .get
      val bookingId = Ulid
        .generate(Clock.fixed(Instant.parse("2026-11-04T00:00:00Z")), new java.util.Random(98))
        .value
      val m =
        s"""mutation { registerBooking(idempotencyKey: "same", bookingId: "$bookingId", shipperId: "$shipperId", itemName: "品",
                 |  weightKg: "1", lengthCm: "1", widthCm: "1", heightCm: "1", quantity: 1, origin: "JPTYO", destination: "NLRTM",
                 |  preferredHandover: "2026-12-01", deadline: "2027-01-15") { bookingId number version } }""".stripMargin
      val (_, r1) = env.command(m)
      val (_, r2) = env.command(m)
      assert(r1 == r2, s"${r1.noSpaces} / ${r2.noSpaces}")
      eventually("1 件の反映")(
        Option(env.query("{ bookings { id } }")._2)
          .filter(_.hcursor.downField("data").downField("bookings").values.exists(_.size == 1))
      )
      Thread.sleep(300)
      assert(
        env
          .query("{ bookings { id } }")
          ._2
          .hcursor
          .downField("data")
          .downField("bookings")
          .values
          .get
          .size == 1
      )
    finally env.stop()
  }

  test("認可: 営業担当者以外は Mutation が FORBIDDEN。荷主の個人情報の Query も FORBIDDEN") {
    val env = new Env
    try
      val (code, r) = env.command(
        """mutation { registerShipper(idempotencyKey: "k", name: "a", address: "b", contact: "c", email: "a@b.com") { shipperId } }""",
        Some("Accountant")
      )
      assert(
        code == 200 && r.hcursor
          .downField("errors")
          .downArray
          .downField("extensions")
          .get[String]("code") == Right("FORBIDDEN")
      )
      val (_, q) = env.query("{ shippers { id } }", Some("TrackingManager"))
      assert(
        q.hcursor
          .downField("errors")
          .downArray
          .downField("extensions")
          .get[String]("code") == Right("FORBIDDEN")
      )
    finally env.stop()
  }
