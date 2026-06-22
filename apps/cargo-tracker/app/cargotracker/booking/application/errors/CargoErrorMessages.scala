package cargotracker.booking.application.errors

import cargotracker.booking.domain.model.aggregates.Cargo

/** `Cargo.Error` → 表示用メッセージ変換を一箇所に集約する。
  *
  * `BookingCommandService` 内の重複（assignToRouting / transition / assignItinerary）を解消する（IT4 セルフレビュー H2 対応）。
  */
object CargoErrorMessages:

  /** 状態遷移など共通エラーをメッセージ化する。`fallback` は `InvalidStatusTransition` 以外で利用するメッセージ。 */
  def toMessage(error: Cargo.Error, fallback: => String): String = error match
    case Cargo.InvalidStatusTransition(from, to) =>
      s"現在の状態 $from から $to への遷移はできません"
    case Cargo.UnknownShipper =>
      fallback

  /** `予約 BK-... の<action>に失敗しました` 形式の fallback メッセージを生成する。 */
  def actionFailureFallback(bookingId: String, action: String): String =
    s"予約 $bookingId の${action}に失敗しました"

  /** 予約 ID パース失敗メッセージ。 */
  def invalidBookingIdMessage(bookingId: String): String =
    s"予約 ID の形式が不正です: $bookingId"

  /** 予約未発見メッセージ。 */
  def bookingNotFoundMessage(bookingId: String): String =
    s"予約 $bookingId が見つかりません"
