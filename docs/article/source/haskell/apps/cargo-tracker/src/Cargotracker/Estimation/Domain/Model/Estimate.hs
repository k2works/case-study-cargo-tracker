{- | Estimate 集約ルート (US01, IT2)

輸送見積を表現する集約。営業担当者が荷主の輸送要件 (出発地・目的地・期限・
貨物種別・重量) を入力すると、システムが利用可能な航海から経路候補を列挙し、
見積として保存する。

集約構造:
- Estimate (root)
  - estimateId : EstimateId (UUID)
  - shipperId  : Text (Shipper BC への ACL 参照、文字列化)
  - origin / destination : UnLocode (共有カーネル)
  - deadline   : UTCTime (期限)
  - cargoType  : Text (US05 と整合、IT3 で sum type 化検討)
  - weightKg   : Double
  - status     : EstimateStatus
  - routeCandidates : [RouteCandidate] (rank 昇順)

ACL 規約:
- Shipper BC の ShipperId を直接 import せず、Application 層で
  Shipper.unShipperId による文字列変換後に保持する (T-06 Rule 4)。
- Routing BC の VoyageNumber も同様に [Text] で保持する。

不変条件:
- routeCandidates が空でない場合、rank は重複なし
- routeCandidates が空の場合は「期限内到達可能経路なし」を表す
-}
module Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate (..),
    mkEstimate,
    adjustConditions,
    replaceCandidates,
  ) where

import Data.List (nub)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate (..),
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateId (EstimateId)
import Cargotracker.Estimation.Domain.Model.Value.EstimateStatus
  ( EstimateStatus (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Estimate = Estimate
  { estimateId :: !EstimateId
  , shipperIdText :: !Text
  , origin :: !UnLocode
  , destination :: !UnLocode
  , deadline :: !UTCTime
  , cargoTypeText :: !Text
  , weightKg :: !Double
  , estimateStatus :: !EstimateStatus
  , routeCandidates :: ![RouteCandidate]
  }
  deriving stock (Eq, Show)

mkEstimate ::
  EstimateId ->
  Text ->
  UnLocode ->
  UnLocode ->
  UTCTime ->
  Text ->
  Double ->
  [RouteCandidate] ->
  Either DomainError Estimate
mkEstimate eid sidT origin' dest deadline' ctypeText weight candidates
  | weight <= 0 =
      Left (InvalidBookingId "Estimate.weightKg must be > 0")
  | not (uniqueRanks candidates) =
      Left (InvalidBookingId "Estimate: route candidate ranks must be unique")
  | otherwise =
      Right
        Estimate
          { estimateId = eid
          , shipperIdText = sidT
          , origin = origin'
          , destination = dest
          , deadline = deadline'
          , cargoTypeText = ctypeText
          , weightKg = weight
          , estimateStatus = Created
          , routeCandidates = candidates
          }
  where
    uniqueRanks rs =
      let ranks = map rank rs in length ranks == length (nub ranks)

{- | US10 (IT8): 経路条件 (到着期限・貨物種別) を調整する純粋関数。

受入基準「条件を調整 (期限延長・経由地追加・貨物種別変更等) して再算出を
実行できる」の条件変更部分。候補の再算出は Application 層
(`AdjustEstimateCommand`) が Routing BC へ問い合わせて `replaceCandidates`
で反映する。
-}
adjustConditions :: UTCTime -> Text -> Estimate -> Either DomainError Estimate
adjustConditions newDeadline newCargoType est
  | T.null (T.strip newCargoType) =
      Left (InvalidRouteAdjustment "empty cargo type")
  | otherwise =
      Right
        est
          { deadline = newDeadline
          , cargoTypeText = T.strip newCargoType
          }

{- | US10 (IT8): 経路候補を丸ごと差し替える。rank 重複は拒否する
(mkEstimate と同じ制約)。空リストは「調整後も期限内到達可能経路なし」を
表し許容する (受入基準 4: 営業担当者への条件協議依頼へ進む)。
-}
replaceCandidates :: [RouteCandidate] -> Estimate -> Either DomainError Estimate
replaceCandidates candidates est
  | not uniqueRanks' =
      Left (InvalidRouteAdjustment "route candidate ranks must be unique")
  | otherwise = Right est {routeCandidates = candidates}
  where
    uniqueRanks' =
      let ranks = map rank candidates in length ranks == length (nub ranks)
