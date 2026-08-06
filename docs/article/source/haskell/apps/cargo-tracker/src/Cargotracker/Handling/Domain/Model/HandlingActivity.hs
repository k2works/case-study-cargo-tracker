{- | 荷役活動集約 (US15, IT5)

domain-model.md §5 Handling Context の HandlingActivity 集約に準拠。
1 件の荷役イベント (誰が / いつ / どこで / どの種別 / どの航海で) を記録する。

ADR-0004 Cross-BC 規約: booking_id / voyage_number / location_unlocode は
Text として保持し、Booking / Routing BC の Domain 型を直接 import しない。

IT5 段階 (最小版):
- 順序制約評価 (前イベントとの整合性チェック) は Application 層で行い、
  ここは値オブジェクトの構築 + 未来時刻検証のみ担当する。
- ItineraryLeg / Voyage との整合性検証は task 5.2 で追加。
-}
module Cargotracker.Handling.Domain.Model.HandlingActivity
  ( HandlingActivity (..),
    mkHandlingActivity,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Handling.Domain.Model.HandlingType (HandlingType)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data HandlingActivity = HandlingActivity
  { haBookingId :: !Text
  -- ^ Cross-BC (Booking BC の BookingId、Text で保持)
  , haEventType :: !HandlingType
  , haCompletionTime :: !UTCTime
  -- ^ 荷役実施完了時刻 (現在時刻より過去である必要がある)
  , haLocationUnlocode :: !Text
  -- ^ Cross-BC (Location UnLocode、Text で保持、5 文字)
  , haVoyageNumber :: !(Maybe Text)
  -- ^ LOAD / UNLOAD 時に必須。Receive / Customs / Claim では Nothing 可
  , haOperatorName :: !Text
  -- ^ 荷役作業員名 (認証済ユーザ名など)
  }
  deriving stock (Eq, Show)

{- | スマートコンストラクタ。以下を検証する:

1. 発生日時が現在時刻 (now) より過去
2. LOAD/UNLOAD の場合は voyageNumber が Just (要必須)
3. operatorName が空でない

順序制約 (前イベントとの整合性) は履歴を伴うため Application 層で検証する。
Itinerary との整合性検証も同様。
-}
mkHandlingActivity ::
  -- | now (現在時刻)
  UTCTime ->
  -- | 予約 ID (Text)
  Text ->
  -- | イベント種別
  HandlingType ->
  -- | 実施時刻
  UTCTime ->
  -- | 場所 UnLocode
  Text ->
  -- | 航海番号 (LOAD/UNLOAD 時は Just 必須)
  Maybe Text ->
  -- | 作業員名
  Text ->
  Either DomainError HandlingActivity
mkHandlingActivity now bid etype completedAt loc mVoyage opName
  | completedAt > now = Left HandlingEventTimeInFuture
  | T.null opName = Left (InvalidBrokerName opName)
  | otherwise =
      Right
        HandlingActivity
          { haBookingId = bid
          , haEventType = etype
          , haCompletionTime = completedAt
          , haLocationUnlocode = loc
          , haVoyageNumber = mVoyage
          , haOperatorName = opName
          }
