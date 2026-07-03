{- | 手動状態更新コマンド (US17, IT7)

Tracker/Admin が Tracking 状態を手動で修正する Application 層ユースケース。

フロー:

1. TrackingRepository.findByTrackingNumber で対象活動を取得
2. Domain の `updateStateManually` で状態遷移と audit を生成 (純粋)
3. TrackingRepository.updateTransportStatus で状態永続化
4. TrackingStateAuditRepository.saveAudit で監査ログ永続化

権限判定 (Tracker/Admin のみ) は Interfaces 層で行う (Session/Role 情報が
必要なため)。本 Command は業務ルール検証のみに集中する。

T-01 準拠: 単一 Tx 統合 (updateTransportStatus + saveAudit) は次反復で
withDbTransaction 経由の TxRunner を注入し実現する。本反復は逐次実行で暫定。
-}
module Cargotracker.Tracking.Application.ManualStateUpdateCommand
  ( ManualStateUpdateInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus)
import Cargotracker.Tracking.Application.Ports
  ( TrackingRepository (..),
  )
import Cargotracker.Tracking.Application.TrackingStateAuditPorts
  ( TrackingStateAuditRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    updateStateManually,
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( mkTrackingNumber,
  )

data ManualStateUpdateInput = ManualStateUpdateInput
  { inputTrackingNumberText :: !Text
  , inputNewStatus :: !TransportStatus
  , inputReason :: !Text
  , inputChangedBy :: !Text
  -- ^ userId (Text-DTO、Rule 4 準拠)
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  TrackingRepository m ->
  TrackingStateAuditRepository m ->
  ManualStateUpdateInput ->
  m (Either DomainError ())
execute trackingRepo auditRepo input =
  case mkTrackingNumber (inputTrackingNumberText input) of
    Left err -> pure (Left err)
    Right tn -> do
      mActivity <- findByTrackingNumber trackingRepo tn
      case mActivity of
        Nothing -> pure (Left (TrackingNotFound (inputTrackingNumberText input)))
        Just activity ->
          case updateStateManually
            (inputNewStatus input)
            (inputReason input)
            (inputChangedBy input)
            (inputNow input)
            activity of
            Left err -> pure (Left err)
            Right (updated, audit) -> do
              updateResult <-
                updateTransportStatus
                  trackingRepo
                  (taBookingId updated)
                  (taTransportStatus updated)
              case updateResult of
                Left err -> pure (Left err)
                Right () -> saveAudit auditRepo audit
