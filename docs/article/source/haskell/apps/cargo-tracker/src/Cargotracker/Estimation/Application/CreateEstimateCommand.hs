{- | 見積作成コマンド (US01, IT2)

業務フロー:
1. 入力 (EstimateId UUID 文字列 / 荷主 ID / 出発地 / 目的地 / 期限 / 貨物種別 / 重量 /
   候補リスト) を受け取る
2. 各値オブジェクトをスマートコンストラクタで構築・検証
3. Estimate 集約 (mkEstimate) を構築
4. EstimateRepository.saveEstimate で永続化

IT2 時点では `RouteCandidate` は呼出側 (Interfaces 層) で生成し
本コマンドに渡す。航海検索ロジック (VoyageRepository から候補列挙) は
IT3 で `RouteSearcher` ポートとして導入する。
-}
module Cargotracker.Estimation.Application.CreateEstimateCommand
  ( CreateEstimateInput (..),
    CandidateInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate,
    mkEstimate,
  )
import Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate,
    mkRouteCandidate,
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateId (mkEstimateId)
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError)

-- 1 つの経路候補に対する入力 DTO
data CandidateInput = CandidateInput
  { inputRank :: !Int
  , inputTransitDays :: !Int
  , inputEstimatedCost :: !Int
  , inputVoyageNumbers :: ![Text]
  }
  deriving stock (Eq, Show)

data CreateEstimateInput = CreateEstimateInput
  { inputEstimateId :: !Text
  , inputShipperId :: !Text
  , inputOrigin :: !Text
  , inputDestination :: !Text
  , inputDeadline :: !UTCTime
  , inputCargoType :: !Text
  , inputWeightKg :: !Double
  , inputCandidates :: ![CandidateInput]
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  EstimateRepository m ->
  CreateEstimateInput ->
  m (Either DomainError Estimate)
execute repo input = case validate input of
  Left e -> pure (Left e)
  Right estimate -> do
    result <- saveEstimate repo estimate
    case result of
      Left e -> pure (Left e)
      Right () -> pure (Right estimate)
  where
    validate :: CreateEstimateInput -> Either DomainError Estimate
    validate i = do
      eid <- mkEstimateId (inputEstimateId i)
      orig <- mkUnLocode (inputOrigin i)
      dest <- mkUnLocode (inputDestination i)
      candidates <- traverse buildCandidate (inputCandidates i)
      mkEstimate
        eid
        (inputShipperId i)
        orig
        dest
        (inputDeadline i)
        (inputCargoType i)
        (inputWeightKg i)
        candidates

    buildCandidate :: CandidateInput -> Either DomainError RouteCandidate
    buildCandidate c =
      mkRouteCandidate
        (inputRank c)
        (inputTransitDays c)
        (inputEstimatedCost c)
        (inputVoyageNumbers c)
