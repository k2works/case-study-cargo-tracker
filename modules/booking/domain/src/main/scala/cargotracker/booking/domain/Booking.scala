package cargotracker.booking.domain

import cargotracker.sharedkernel.Ulid
import java.time.Instant

/** 全イベントが持つもの：イベント ID・発生時刻・実行者（ドメインモデル 10.1、NFR-S10）。 実行者は利用者の ID。システムの反応（人が起こしていない）は None。
  */
final case class EventMeta(eventId: Ulid, occurredAt: Instant, actor: Option[Ulid] = None)

/** 予約の状態（要件定義書の状態モデルを正とする。Bolt 1 は仮受付まで）。 */
enum BookingStatus:
  case Received

final case class BookingData(
    id: BookingId,
    number: BookingNumber,
    shipper: ShipperRef,
    cargo: CargoSpec,
    transport: TransportRequest,
    status: BookingStatus,
    registeredAt: Instant
)

enum BookingState:
  case NotCreated
  case Received(booking: BookingData)

object BookingState:
  /** 状態の名前。match が網羅的なので、状態を足すとコンパイル時に更新漏れが分かる。 */
  def name(state: BookingState): String = state match
    case NotCreated  => "NotCreated"
    case Received(_) => "Received"

sealed trait BookingCommand

object BookingCommand:
  final case class RegisterBooking(
      meta: EventMeta,
      id: BookingId,
      number: BookingNumber,
      shipper: ShipperRef,
      cargo: CargoSpec,
      transport: TransportRequest
  ) extends BookingCommand

  def name(command: BookingCommand): String = command match
    case _: RegisterBooking => "RegisterBooking"

/** 予約のイベント。全イベントがイベント ID・集約 ID・発生時刻を持つ（ドメインモデル 10.1）。個人情報は載せない。 */
sealed trait BookingEvent:
  def meta: EventMeta
  def bookingId: BookingId

object BookingEvent:
  final case class BookingRegistered(
      meta: EventMeta,
      id: BookingId,
      number: BookingNumber,
      shipper: ShipperRef,
      cargo: CargoSpec,
      transport: TransportRequest
  ) extends BookingEvent:
    def bookingId: BookingId = id

enum BookingError:
  /** 状態遷移の違反（B1）。状態モデルにない遷移は、すべてこれで拒む。 */
  case InvalidTransition(from: String, command: String)

/** 貨物予約の振る舞い。純粋な関数だけで、Pekko にも DB にも時計にも依存しない。 */
object Booking:

  def handle(
      state: BookingState,
      command: BookingCommand
  ): Either[BookingError, (BookingState, BookingEvent)] =
    (state, command) match
      case (BookingState.NotCreated, c: BookingCommand.RegisterBooking) =>
        val event =
          BookingEvent.BookingRegistered(c.meta, c.id, c.number, c.shipper, c.cargo, c.transport)
        Right((evolve(state, event), event))
      case (s, c) =>
        Left(BookingError.InvalidTransition(BookingState.name(s), BookingCommand.name(c)))

  def evolve(state: BookingState, event: BookingEvent): BookingState = (state, event) match
    case (_, e: BookingEvent.BookingRegistered) =>
      BookingState.Received(
        BookingData(
          e.id,
          e.number,
          e.shipper,
          e.cargo,
          e.transport,
          BookingStatus.Received,
          e.meta.occurredAt
        )
      )

  /** 実行できる操作（DM-10：読み取りモデルの `available_commands` の元。画面が状態を再判定しない）。 実装済みの操作だけを返す。Bolt 1
    * には、登録の後に実行できる操作がまだ無い（後の Bolt で足す）。
    */
  def availableCommands(state: BookingState): List[String] = state match
    case BookingState.NotCreated  => Nil
    case BookingState.Received(_) => Nil

  /** イベントの列から状態を組み立てる（本番の復元と同じ経路）。 */
  def replay(events: Seq[BookingEvent]): BookingState =
    events.foldLeft[BookingState](BookingState.NotCreated)(evolve)
