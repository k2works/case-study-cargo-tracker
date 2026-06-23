package cargotracker.handling.domain.model.ports

import java.time.Instant

/** Handling Context が Tracking から必要とする問合せ・通知の出力ポート（ADR ヘキサゴナル / IT7 0.3）。
  *
  * Handling Context の `application` 層はこのポートのみに依存し、Tracking Context の実装には `infrastructure.acl` 層のアダプター経由でアクセスする。
  */
trait TrackingLookupPort:
  /** 追跡番号から関連する予約 ID を解決する。 */
  def findBookingIdByTrackingNumber(trackingNumber: String): Option[String]

  /** 追跡イベントを Tracking 側に追記する。 */
  def recordEvent(
      trackingNumber: String,
      eventType: String,
      eventTime: Instant,
      locationUnLocode: String,
      voyageNumber: Option[String],
      routeDeviation: Boolean
  ): Either[String, Unit]
