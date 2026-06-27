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
  ) where

import Data.List (nub)
import Data.Text (Text)
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
