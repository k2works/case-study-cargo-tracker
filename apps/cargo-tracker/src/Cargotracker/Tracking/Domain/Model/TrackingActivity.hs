{- | 追跡活動集約 (US14, IT5 最小版)

追跡番号を軸とする Tracking Context の集約ルート。
IT5 では US14 (追跡番号発行) のみ実装、US15/16 の荷役履歴・確認コード保持は
`markUsedInTransit` / `linkConfirmationCode` を後続 iter で追加する形で拡張可能な設計とする。

domain-model.md §4 の記述:
  * 追跡番号は 1 予約 = 0..1 の関係
  * currentStatus は「イベント履歴と未解決例外から導出する純粋関数」として設計
  * IT5 段階では events/exceptions は空 [] で初期化、TransportStatus は
    tracking_activity.transport_status カラムのフォールバック値を返す
-}
module Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    initialActivity,
    currentStatus,
    updateStateManually,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..), transportStatusToText)
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit (TrackingStateAudit (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (TrackingNumber)

data TrackingActivity = TrackingActivity
  { taTrackingNumber :: !TrackingNumber
  , taBookingId :: !Text
  -- ^ 業務キー (BookingId newtype を使わない: Cross-BC 参照は Text = ADR-0004)
  , taTransportStatus :: !TransportStatus
  , taVersion :: !Int
  -- ^ 楽観ロック用
  }
  deriving stock (Eq, Show)

{- | 予約確定時の新規追跡活動を作成する。TransportStatus は
`TsNotReceived` で初期化 (US15 の Receive イベント登録で `TsReceived` へ遷移)。
-}
initialActivity :: TrackingNumber -> Text -> TrackingActivity
initialActivity tn bid =
  TrackingActivity
    { taTrackingNumber = tn
    , taBookingId = bid
    , taTransportStatus = TsNotReceived
    , taVersion = 0
    }

-- | domain-model.md §4 の currentStatus。IT5 段階ではカラム値を返すのみ。
currentStatus :: TrackingActivity -> TransportStatus
currentStatus = taTransportStatus

{- | 手動状態更新 (US17, IT7)。

Tracker/Admin が Tracking 状態を手動で修正する。監査ログ (TrackingStateAudit) を
副産物として返し、Application 層で tracking_state_audit テーブルに永続化する
(Tx 内で TrackingActivity 更新と同時に、ADR-0014 参照)。

失敗パス:

* 同じ状態への遷移 (`newStatus == currentStatus`) → StateAlreadyMatches
* 空文字の理由 → ManualUpdateReasonRequired

権限判定 (Tracker/Admin のみ) は Application 層で行う。Domain は理由と状態のみを
検証する純粋関数。
-}
updateStateManually ::
  -- | 新しい状態
  TransportStatus ->
  -- | 変更理由 (空文字は不可)
  Text ->
  -- | changedBy (userId、Text-DTO)
  Text ->
  -- | now
  UTCTime ->
  TrackingActivity ->
  Either DomainError (TrackingActivity, TrackingStateAudit)
updateStateManually newStatus reason userId now ta
  | newStatus == taTransportStatus ta =
      Left (StateAlreadyMatches (transportStatusToText newStatus))
  | T.null (T.strip reason) = Left ManualUpdateReasonRequired
  | otherwise =
      let updated = ta {taTransportStatus = newStatus, taVersion = taVersion ta + 1}
          audit =
            TrackingStateAudit
              { tsaTrackingNumber = taTrackingNumber ta
              , tsaPreviousStatus = taTransportStatus ta
              , tsaNewStatus = newStatus
              , tsaReason = reason
              , tsaChangedBy = userId
              , tsaChangedAt = now
              }
       in Right (updated, audit)
