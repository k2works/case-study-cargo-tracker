{- | 輸送状態 (Shared Domain / IT5 US14)

domain-model.md §8 Shared Domain の TransportStatus に準拠。
9 値の sum type、コンテキスト間連携 (イベントペイロード) と画面表示の公開語彙。

Tracking Context の内部 `TrackingStatus` とは意図的に別の型 (H-01 / ADR 未起票):
  * TrackingStatus (Tracking Context 固有): currentStatus がイベント履歴から導出
  * TransportStatus (Shared): 出口で trackingStatusToTransportStatus により変換

IT5 段階では TrackingStatus 未実装のため、TransportStatus のみで tracking_activity
カラムを直接管理する簡略構成 (IT6 以降で Tracking 内部型を分離)。
-}
module Cargotracker.Shared.Domain.TransportStatus
  ( TransportStatus (..),
    transportStatusToText,
    textToTransportStatus,
  ) where

import Data.Text (Text)

data TransportStatus
  = TsNotReceived
  | TsReceived
  | TsLoaded
  | TsOnboardCarrier
  | TsUnloaded
  | TsAwaitingClaim
  | TsClaimed
  | TsInException
  | TsUnknown
  deriving stock (Eq, Show, Read, Enum, Bounded)

-- | DB CHECK 制約と一致するテキスト表現。
transportStatusToText :: TransportStatus -> Text
transportStatusToText TsNotReceived = "TsNotReceived"
transportStatusToText TsReceived = "TsReceived"
transportStatusToText TsLoaded = "TsLoaded"
transportStatusToText TsOnboardCarrier = "TsOnboardCarrier"
transportStatusToText TsUnloaded = "TsUnloaded"
transportStatusToText TsAwaitingClaim = "TsAwaitingClaim"
transportStatusToText TsClaimed = "TsClaimed"
transportStatusToText TsInException = "TsInException"
transportStatusToText TsUnknown = "TsUnknown"

-- | DB 復元用。想定外値は TsUnknown にフォールバック (CHECK 制約で保証されている前提)。
textToTransportStatus :: Text -> TransportStatus
textToTransportStatus "TsNotReceived" = TsNotReceived
textToTransportStatus "TsReceived" = TsReceived
textToTransportStatus "TsLoaded" = TsLoaded
textToTransportStatus "TsOnboardCarrier" = TsOnboardCarrier
textToTransportStatus "TsUnloaded" = TsUnloaded
textToTransportStatus "TsAwaitingClaim" = TsAwaitingClaim
textToTransportStatus "TsClaimed" = TsClaimed
textToTransportStatus "TsInException" = TsInException
textToTransportStatus _ = TsUnknown
