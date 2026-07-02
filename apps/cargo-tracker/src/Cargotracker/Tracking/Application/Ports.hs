{- | Tracking Context 出力ポート (US14, IT5)

Application 層が Infrastructure に依存しないための型クラス相当のレコード
ポート。Postgres 実装は Infrastructure 層で提供する。

T-02 規約: Repository 関数は IO のみ。Tx 境界は Application が管理する。
-}
module Cargotracker.Tracking.Application.Ports
  ( TrackingRepository (..),
    queryTrackingNumberText,
    markClaimedByBookingId,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber (..),
  )

data TrackingRepository m = TrackingRepository
  { saveTracking :: TrackingActivity -> m (Either DomainError ())
  -- ^ 新規追跡活動を保存。同 booking_id への重複は Application 層で事前チェック。
  , findByBookingId :: Text -> m (Maybe TrackingActivity)
  , findByTrackingNumber :: TrackingNumber -> m (Maybe TrackingActivity)
  , updateTransportStatus :: Text -> TransportStatus -> m (Either DomainError ())
  {- ^ T5-04 (IT6): booking_id を業務キーに transport_status を更新する。
  Handling BC からは Cross-BC helper `markClaimedByBookingId` 経由でのみ
  呼ぶことを推奨する。
  -}
  }

{- | Cross-BC 境界を跨いだ Text 変換ヘルパー (ADR-0004 Cross-BC 規約 / Rule 4 準拠)。

Booking BC の Interfaces 層 (BookingPageApi handlerShow) が追跡番号を表示するために
呼び出す。Tracking BC の Domain 型 (TrackingActivity / TrackingNumber) を境界外に
露出させず、`Maybe Text` のみ返すことで Rule 4 (BC Domain 直接 import 禁止) を守る。
-}
queryTrackingNumberText ::
  Monad m => TrackingRepository m -> Text -> m (Maybe Text)
queryTrackingNumberText repo bid = do
  mActivity <- findByBookingId repo bid
  pure (unTrackingNumber . taTrackingNumber <$> mActivity)

{- | Cross-BC helper (T5-04, ADR-0012 決定 4): booking_id の TrackingActivity を
「引取済 (TsClaimed)」に遷移させる。

Handling BC の VerifyClaimAndRegisterCommand から Tx 境界内で呼ばれる。
戻り値:

- Right (): 遷移成功
- Left (HandlingBookingNotFound bid): 該当 tracking_activity なし
- Left (PersistenceFailed _): DB エラー (呼出側で例外変換 or 再スロー)

Cargo.status への波及は本イテレーション対象外 (IT8 US23 精算処理で対応、
ADR-0012 決定 4 明記)。
-}
markClaimedByBookingId ::
  Monad m => TrackingRepository m -> Text -> m (Either DomainError ())
markClaimedByBookingId repo bid = do
  mActivity <- findByBookingId repo bid
  case mActivity of
    Nothing -> pure (Left (HandlingBookingNotFound bid))
    Just _ -> updateTransportStatus repo bid TsClaimed
