package cargotracker.booking.commandadapter

import cargotracker.booking.domain.{BookingId, BookingNumber, ShipperId}
import cargotracker.booking.usecase.*
import cargotracker.sharedkernel.{Clock, Ulid}
import io.circe.{Json, parser}
import java.sql.Timestamp
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api.*

/** 冪等性キーの保存先（`platform.idempotency_key`。ADR-003）。保持は 7 日（NFR-D02）。 同じキーに 2
  * 度目の保存があっても最初の結果を残す（上書きしない）。 注：確認（find）と保存（save）は別の操作なので、**同じキーの要求が同時に走ると両方が実行されうる**。
  * 実行の前にキーを予約する方式は、実測と運用の結果を見て Bolt 後半に検討する。
  */
final class SlickIdempotencyStore(db: Database, clock: Clock)(using ExecutionContext)
    extends IdempotencyStore:
  private val retention = Duration.ofDays(7)

  def find(owner: Ulid, key: String): Future[Option[StoredResult]] =
    val now = Timestamp.from(clock.now())
    db.run(
      sql"""SELECT request_hash, result::text FROM platform.idempotency_key
            WHERE principal_user_id = ${owner.value} AND idempotency_key = $key AND expires_at > $now"""
        .as[(String, String)]
        .headOption
    ).map(_.map((hash, json) => StoredResult(hash, decode(json))))

  def save(owner: Ulid, key: String, result: StoredResult): Future[Unit] =
    val now = clock.now()
    val (name, json) = encode(result.output)
    db.run(
      sqlu"""INSERT INTO platform.idempotency_key
               (principal_user_id, idempotency_key, command_name, request_hash, result, created_at, expires_at)
             VALUES (${owner.value}, $key, $name, ${result.requestHash}, ${json.noSpaces}::jsonb,
                     ${Timestamp.from(now)}, ${Timestamp.from(now.plus(retention))})
             ON CONFLICT (principal_user_id, idempotency_key) DO UPDATE
               SET command_name = EXCLUDED.command_name, request_hash = EXCLUDED.request_hash,
                   result = EXCLUDED.result, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
             WHERE platform.idempotency_key.expires_at <= ${Timestamp.from(now)}"""
    ).map(_ => ())

  private def encode(output: IdempotentOutput): (String, Json) = output match
    case IdempotentOutput.ShipperRegistered(id) =>
      (
        "RegisterShipper",
        Json.obj(
          "type" -> Json.fromString("ShipperRegistered"),
          "shipperId" -> Json.fromString(id.value.value)
        )
      )
    case IdempotentOutput.BookingRegistered(id, number, version) =>
      (
        "RegisterBooking",
        Json.obj(
          "type" -> Json.fromString("BookingRegistered"),
          "bookingId" -> Json.fromString(id.value.value),
          "number" -> Json.fromString(number.value),
          "version" -> Json.fromLong(version)
        )
      )

  /** 保存した結果は自分が書いたもの。壊れていたら握りつぶさず、原因が分かる例外で失敗させる。 */
  private def decode(text: String): IdempotentOutput =
    def bad(why: String) = new IllegalStateException(s"冪等性キーの結果が不正（$why）: $text")
    val c = parser.parse(text).fold(e => throw bad(e.message), _.hcursor)
    def str(f: String) = c.get[String](f).fold(_ => throw bad(f), identity)
    def ulid(f: String) = Ulid.parse(str(f)).fold(e => throw bad(e.message), identity)
    str("type") match
      case "ShipperRegistered" => IdempotentOutput.ShipperRegistered(ShipperId(ulid("shipperId")))
      case "BookingRegistered" =>
        IdempotentOutput.BookingRegistered(
          BookingId(ulid("bookingId")),
          BookingNumber.parse(str("number")).fold(e => throw bad(e.message), identity),
          c.get[Long]("version").fold(_ => throw bad("version"), identity)
        )
      case other => throw bad(s"type=$other")

/** 予約番号の発行（`platform.booking_number_seq`）。形式は `BK-` + 6 桁の**仮**（Bolt 計画 Q-2。整形はここに閉じ込める）。 */
final class SequenceBookingNumberIssuer(db: Database)(using ExecutionContext)
    extends BookingNumberIssuer:
  def next(): Future[BookingNumber] =
    db.run(sql"SELECT nextval('platform.booking_number_seq')".as[Long].head).map { n =>
      BookingNumber
        .parse(f"BK-$n%06d")
        .fold(e => throw new IllegalStateException(e.message), identity)
    }

/** 集約 ID（ULID）の生成。時刻は Clock から取る。 */
final class SecureIdGenerator(clock: Clock) extends IdGenerator:
  def next(): Ulid = Ulid.generate(clock)

/** 参照データ（`reference.port`）にある港か。 */
final class SlickPortCatalog(db: Database)(using ExecutionContext) extends PortCatalog:
  def exists(code: cargotracker.sharedkernel.PortCode): Future[Boolean] =
    db.run(
      sql"SELECT EXISTS (SELECT 1 FROM reference.port WHERE port_code = ${code.value})"
        .as[Boolean]
        .head
    )
