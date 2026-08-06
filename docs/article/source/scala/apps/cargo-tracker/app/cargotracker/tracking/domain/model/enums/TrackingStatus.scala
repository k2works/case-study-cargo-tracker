package cargotracker.tracking.domain.model.enums

/** 追跡状態（domain-model.md L710-720）。
  *
  *   - 9 段階の追跡フェーズ。`TrackingActivity.currentStatus()` がイベント履歴から導出する
  *   - IT5 では `NotReceived` のみ初期値として利用。`Received` / `Loaded` 等は US15 で `addEvent` 経由で遷移する
  */
enum TrackingStatus:
  case NotReceived, Received, Loaded, OnboardCarrier, Unloaded, AwaitingClaim, Claimed, InException, Unknown
