package cargotracker.booking.queryadapter

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.PostgresProfile.api.*

/** 読み取りモデルの問い合わせ結果（画面に出す形）。 */
final case class ShipperSummary(id: String, name: String, email: String, kind: String)
final case class ShipperDetail(
    id: String,
    kind: String,
    name: String,
    address: String,
    contact: String,
    email: String
)

final case class BookingSummary(
    id: String,
    number: String,
    shipperId: String,
    status: String,
    origin: String,
    destination: String,
    deadline: String,
    version: Long,
    availableCommands: List[String]
)
final case class CargoView(itemName: String, weightKg: String, quantity: Int)
final case class HistoryEntry(
    eventId: String,
    kind: String,
    occurredAt: Instant,
    actorUserId: Option[String]
)
final case class BookingDetail(
    summary: BookingSummary,
    cargo: CargoView,
    preferredHandover: String,
    history: List[HistoryEntry]
)

/** 荷主の読み取り（`booking.shipper`。個人情報を含むので、呼び出し側（QueryService）が認可する）。 */
final class ShipperQueries(db: Database)(using ExecutionContext):

  private given GetResult[ShipperSummary] = new GetResult[ShipperSummary]:
    def apply(r: PositionedResult): ShipperSummary =
      ShipperSummary(r.nextString(), r.nextString(), r.nextString(), r.nextString())
  private given GetResult[ShipperDetail] = new GetResult[ShipperDetail]:
    def apply(r: PositionedResult): ShipperDetail =
      ShipperDetail(
        r.nextString(),
        r.nextString(),
        r.nextString(),
        r.nextString(),
        r.nextString(),
        r.nextString()
      )

  /** 名前またはメールの部分一致（大文字小文字を区別しない）。`%` と `_` はワイルドカードでなく文字として扱う。 */
  def search(keyword: Option[String], limit: Int): Future[List[ShipperSummary]] =
    val pattern = keyword.map(_.trim).filter(_.nonEmpty).map(k => "%" + escapeLike(k) + "%")
    val q = pattern match
      case Some(p) =>
        sql"""SELECT shipper_id, name, email, shipper_type FROM booking.shipper
              WHERE deleted_at IS NULL AND (name ILIKE $p ESCAPE '\' OR email_normalized ILIKE $p ESCAPE '\')
              ORDER BY created_at, shipper_id LIMIT $limit""".as[ShipperSummary]
      case None =>
        sql"""SELECT shipper_id, name, email, shipper_type FROM booking.shipper
              WHERE deleted_at IS NULL ORDER BY created_at, shipper_id LIMIT $limit"""
          .as[ShipperSummary]
    db.run(q).map(_.toList)

  def detail(id: String): Future[Option[ShipperDetail]] =
    db.run(
      sql"""SELECT shipper_id, shipper_type, name, address, contact, email FROM booking.shipper
            WHERE shipper_id = $id AND deleted_at IS NULL""".as[ShipperDetail].headOption
    )

  private def escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** 予約の読み取り（`booking.booking_view`・`booking_history`）。 */
final class BookingQueries(db: Database)(using ExecutionContext):

  private given GetResult[BookingSummary] = new GetResult[BookingSummary]:
    def apply(r: PositionedResult): BookingSummary =
      val id = r.nextString()
      val number = r.nextString()
      val shipperId = r.nextString()
      val status = r.nextString()
      val origin = r.nextString().trim
      val dest = r.nextString().trim
      val deadline = r.nextDate().toLocalDate.toString
      val version = r.nextLong()
      val commands =
        r.nextString().stripPrefix("{").stripSuffix("}").split(",").filter(_.nonEmpty).toList
      BookingSummary(id, number, shipperId, status, origin, dest, deadline, version, commands)

  private val summaryColumns =
    "booking_id, booking_number, shipper_id, status, origin_port, destination_port, deadline, aggregate_version, available_commands::text"

  def list(
      status: Option[String],
      shipperId: Option[String],
      limit: Int
  ): Future[List[BookingSummary]] =
    db.run(
      sql"""SELECT #$summaryColumns FROM booking.booking_view
            WHERE (${status.isEmpty} OR status = ${status.getOrElse("")})
              AND (${shipperId.isEmpty} OR shipper_id = ${shipperId.getOrElse("")})
            ORDER BY updated_at DESC, booking_id LIMIT $limit""".as[BookingSummary]
    ).map(_.toList)

  def detail(id: String): Future[Option[BookingDetail]] =
    val summary = sql"SELECT #$summaryColumns FROM booking.booking_view WHERE booking_id = $id"
      .as[BookingSummary]
      .headOption
    val extra =
      sql"SELECT item_name, weight_kg::text, quantity, preferred_handover_date FROM booking.booking_view WHERE booking_id = $id"
        .as[(String, String, Int, java.sql.Date)]
        .headOption
    val history =
      sql"SELECT event_id, kind, occurred_at, actor_user_id FROM booking.booking_history WHERE booking_id = $id ORDER BY occurred_at, event_id"
        .as[(String, String, java.sql.Timestamp, Option[String])]
    db.run(summary.zip(extra).zip(history)).map {
      case ((Some(s), Some((item, weight, qty, handover))), h) =>
        Some(
          BookingDetail(
            s,
            CargoView(item, weight, qty),
            handover.toLocalDate.toString,
            h.map((e, k, t, a) => HistoryEntry(e, k, t.toInstant, a)).toList
          )
        )
      case _ => None
    }
