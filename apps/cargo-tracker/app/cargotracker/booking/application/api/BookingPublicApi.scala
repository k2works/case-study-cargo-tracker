package cargotracker.booking.application.api

import cargotracker.booking.domain.model.aggregates.Cargo

/** Booking Context の公開 API (IT8 0.3 / ADR 0017 / H3 解消)。
  *
  * Handling / Tracking / Billing 等の他コンテキストが Booking 内部実装 (`BookingCommandService`) に直接依存することを禁じ、 本 trait のみを ACL
  * Adapter から呼び出す。
  *
  *   - 各 application service (`BookingCommandService` 等) は本 trait を implement する
  *   - 他コンテキストの infrastructure/acl 配下にある Adapter (`BookingAdapter` 等) は本 trait のみに依存する
  *   - 内部メソッドの追加・変更時にこの trait のシグネチャが変わらなければ、 他コンテキストへの波及はゼロになる
  *
  * 関連: ADR 0017、IT7 実装レビュー H3 (xp-architect)、IT8 タスク 0.3。
  */
trait BookingPublicApi:
  /** 荷役発生通知を Booking 側で記録する（US15）。Cargo の状態遷移は行わない。 */
  def logHandlingNotification(
      bookingId: String,
      trackingNumber: String,
      eventType: String,
      location: String
  ): Either[String, Unit]

  /** 引取完了で Cargo を Delivered 遷移 + 配送完了通知を記録する（US16）。 */
  def completeDelivery(
      bookingId: String,
      trackingNumber: String,
      location: String,
      recipientConfirmation: String
  ): Either[String, Cargo]
