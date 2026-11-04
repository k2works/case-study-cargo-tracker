package cargotracker.booking.queryadapter

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.Clock
import java.sql.Timestamp
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api.*

/** 投影に渡すイベント：イベントと、そのイベントを出した集約の連番（集約の版）。 */
final case class ProjectedEvent(event: BookingEvent, sequenceNr: Long)

enum ProjectionResult:
  /** 適用した。 */
  case Applied

  /** 適用済み（再配信・リプレイ）。何もしない。 */
  case AlreadyApplied

  /** 版が飛んでいる。適用せず保留する（呼び出し側が再取得か待避を決める。欠けを埋めない）。 */
  case Gap(expected: Long, actual: Long)

/** 反映遅延の計測点（NFR-P10）：イベントの発生時刻と、読み取りモデルへの反映時刻の差。 */
trait LagRecorder:
  def record(lag: Duration): Unit

/** 予約の読み取りモデルへの投影（`booking.booking_view`・`booking.booking_history`）。 イベントは再配信される前提で冪等に適用する（バックエンド
  * 6.2・DM-03）： 集約の版が「保存済みの版 + 1」のときだけ適用し、適用済みは何もせず、飛んでいれば保留する。 失敗（外部キー違反など）は握りつぶさず、Future
  * の失敗として呼び出し側（リトライと待避）に返す。
  */
final class BookingProjection(db: Database, clock: Clock, lag: LagRecorder)(using ExecutionContext):

  def apply(projected: ProjectedEvent): Future[ProjectionResult] =
    projected.event match
      case e: BookingEvent.BookingRegistered => registered(e, projected.sequenceNr)

  private def registered(e: BookingEvent.BookingRegistered, seqNr: Long): Future[ProjectionResult] =
    val action: DBIO[ProjectionResult] =
      if seqNr != 1L then
        // 登録は集約の最初のイベント（版 1）。それ以外は、版が飛んでいるか、既に適用済み。
        sql"SELECT aggregate_version FROM booking.booking_view WHERE booking_id = ${e.id.value.value}"
          .as[Long]
          .headOption
          .map {
            case Some(v) if seqNr <= v => ProjectionResult.AlreadyApplied
            case _                     => ProjectionResult.Gap(expected = 1L, actual = seqNr)
          }
      else insertRegistered(e)
    db.run(action.transactionally).map { result =>
      if result == ProjectionResult.Applied then
        lag.record(Duration.between(e.meta.occurredAt, clock.now()))
      result
    }

  private def insertRegistered(e: BookingEvent.BookingRegistered): DBIO[ProjectionResult] =
    val common = e.cargo match
      case CargoSpec.General(c) => c
    val at = Timestamp.from(e.meta.occurredAt)
    val commands = Booking.availableCommands(Booking.replay(Seq(e))).mkString(",")
    val insertView =
      sqlu"""INSERT INTO booking.booking_view
               (booking_id, booking_number, shipper_id, cargo_kind, item_name, weight_kg, length_cm, width_cm, height_cm,
                quantity, origin_port, destination_port, preferred_handover_date, deadline, status,
                available_commands, aggregate_version, last_event_id, created_at, updated_at)
             VALUES (${e.id.value.value}, ${e.number.value}, ${e.shipper.id.value.value}, 'GENERAL', ${common.itemName},
                ${common.weightKg}, ${common.dimensions.lengthCm}, ${common.dimensions.widthCm}, ${common.dimensions.heightCm},
                ${common.quantity}, ${e.transport.origin.value}, ${e.transport.destination.value},
                ${java.sql.Date.valueOf(e.transport.preferredHandover)}, ${java.sql.Date.valueOf(
          e.transport.deadline
        )}, 'RECEIVED',
                coalesce(string_to_array($commands, ','), ARRAY[]::text[]), 1, ${e.meta.eventId.value}, $at, $at)
             ON CONFLICT (booking_id) DO NOTHING"""
    val insertHistory =
      sqlu"""INSERT INTO booking.booking_history (event_id, booking_id, occurred_at, actor_user_id, kind)
             VALUES (${e.meta.eventId.value}, ${e.id.value.value}, $at, ${e.meta.actor.map(
          _.value
        )}, 'REGISTERED')
             ON CONFLICT (event_id) DO NOTHING"""
    insertView.flatMap {
      case 0 => DBIO.successful(ProjectionResult.AlreadyApplied) // 行が既にある（再配信）。競合しても二重に作らない
      case _ => insertHistory.map(_ => ProjectionResult.Applied)
    }
