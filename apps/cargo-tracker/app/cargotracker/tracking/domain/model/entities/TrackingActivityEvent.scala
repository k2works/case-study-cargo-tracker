package cargotracker.tracking.domain.model.entities

import cargotracker.tracking.domain.model.valueobjects.TrackingLocation

import java.time.Instant

/** 追跡イベント（Tracking Context 集約内エンティティ、domain-model.md L680 / L731）。
  *
  *   - `Handling Context` の `HandlingActivityRegisteredEvent` を受領して `TrackingActivity.addEvent` で時系列に追記される
  *   - `eventType` は荷役種別の文字列（Tracking 側は HandlingType を直接参照しない / コンテキスト分離）
  */
final case class TrackingActivityEvent(
    eventType: String,
    eventTime: Instant,
    location: TrackingLocation,
    voyageNumber: Option[String],
    routeDeviation: Boolean
)
