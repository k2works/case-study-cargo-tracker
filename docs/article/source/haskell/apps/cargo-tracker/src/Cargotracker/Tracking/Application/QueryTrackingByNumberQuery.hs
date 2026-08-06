{- | 追跡番号による追跡情報照会クエリ (US18, IT5)

公開ページ (`/public/tracking/{trackingNumber}`) からの認証不要照会。
Tracking BC の TrackingRepository を再利用して TrackingActivity を取得し、
公開向け DTO に変換する。

CQRS のクエリ側:
- ドメイン集約を経由せず、公開向け DTO を組み立てる
- IT5 段階では transport_status カラム値をそのまま返す
- IT6 で tracking_handling_event 履歴 + currentStatus 導出関数に置き換え予定

T-01 準拠 (Tx 不要な純粋読取)。
-}
module Cargotracker.Tracking.Application.QueryTrackingByNumberQuery
  ( TrackingView (..),
    execute,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus
  ( TransportStatus,
    transportStatusToText,
  )
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber (..),
    mkTrackingNumber,
  )

{- | 公開ページ向け DTO。
Domain 型 (TrackingActivity / TrackingNumber) を境界外に漏らさず、Text ベースで表現する。
-}
data TrackingView = TrackingView
  { tvTrackingNumber :: !Text
  , tvBookingId :: !Text
  , tvStatusText :: !Text
  -- ^ TransportStatus の Text 表現 ("TsNotReceived" 等)
  , tvStatus :: !TransportStatus
  -- ^ View 側でラベル変換に使う (公開ページ向けにローカライズ)
  }
  deriving stock (Eq, Show)

{- | 追跡番号文字列で照会する。

戻り値:
- @Right (Just view)@ : 追跡情報が見つかり公開可能
- @Right Nothing@     : 追跡番号は形式的に有効だが該当なし (404 に相当)
- @Left err@          : 追跡番号形式不正 (400 相当、実装は Interfaces 側で 404 に統一可)
-}
execute ::
  Monad m =>
  TrackingRepository m ->
  Text ->
  m (Either DomainError (Maybe TrackingView))
execute repo raw =
  case mkTrackingNumber raw of
    Left err -> pure (Left err)
    Right tn -> do
      mActivity <- findByTrackingNumber repo tn
      pure (Right (fmap toView mActivity))
  where
    toView a =
      TrackingView
        { tvTrackingNumber = unTrackingNumber (taTrackingNumber a)
        , tvBookingId = taBookingId a
        , tvStatusText = transportStatusToText (taTransportStatus a)
        , tvStatus = taTransportStatus a
        }
