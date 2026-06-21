package cargotracker.booking.domain.model.aggregates

import cargotracker.booking.domain.model.valueobjects.{BookingId, NotificationType}

import java.time.Instant

/** 通知ログ（US12 / US13）。Booking Context 内の集約。
  *
  *   - 業務キー: 自動採番 ID（リポジトリで採番）。同一予約に対し複数通知を時系列で蓄積する。
  *   - `payload`: 通知本文（経路概要・料金概算等を JSON 化した文字列）
  *   - `version`: 楽観ロック用（更新シナリオは現状ないが将来の再送制御用）
  *   - IT4 はメール送信を行わず DB ログのみ。IT5 以降で MailHog 経由のメール送信を追加する。
  */
final case class NotificationLog private (
    bookingId: BookingId,
    notificationType: NotificationType,
    sentAt: Instant,
    payload: String,
    version: Int
)

object NotificationLog:

  sealed trait Error
  case object EmptyPayload extends Error

  /** 新規通知ログを生成する（version = 0）。 */
  def create(
      bookingId: BookingId,
      notificationType: NotificationType,
      sentAt: Instant,
      payload: String
  ): Either[Error, NotificationLog] =
    if payload.isEmpty then Left(EmptyPayload)
    else Right(new NotificationLog(bookingId, notificationType, sentAt, payload, version = 0))

  /** 永続化からの再構成。 */
  def reconstruct(
      bookingId: BookingId,
      notificationType: NotificationType,
      sentAt: Instant,
      payload: String,
      version: Int
  ): NotificationLog =
    new NotificationLog(bookingId, notificationType, sentAt, payload, version)
