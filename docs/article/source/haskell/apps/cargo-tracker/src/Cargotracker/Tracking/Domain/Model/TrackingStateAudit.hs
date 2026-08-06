{- | TrackingActivity の手動状態更新監査ログ (US17, IT7)

Tracker/Admin による手動状態更新の際に生成される監査エンティティ。
tracking_state_audit テーブル (IT7 追加予定) に永続化される。

domain-model.md §Tracking / iteration_plan-7.md §5.1 に対応。
-}
module Cargotracker.Tracking.Domain.Model.TrackingStateAudit
  ( TrackingStateAudit (..),
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.TransportStatus (TransportStatus)
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (TrackingNumber)

data TrackingStateAudit = TrackingStateAudit
  { tsaTrackingNumber :: !TrackingNumber
  , tsaPreviousStatus :: !TransportStatus
  , tsaNewStatus :: !TransportStatus
  , tsaReason :: !Text
  , tsaChangedBy :: !Text
  -- ^ userId (Cross-BC 参照は Text = ADR-0004 Rule 4)
  , tsaChangedAt :: !UTCTime
  }
  deriving stock (Eq, Show)
