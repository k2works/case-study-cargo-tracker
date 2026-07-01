{- | Tracking Context 出力ポート (US14, IT5)

Application 層が Infrastructure に依存しないための型クラス相当のレコード
ポート。Postgres 実装は Infrastructure 層で提供する。

T-02 規約: Repository 関数は IO のみ。Tx 境界は Application が管理する。
-}
module Cargotracker.Tracking.Application.Ports
  ( TrackingRepository (..),
    queryTrackingNumberText,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError)
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
