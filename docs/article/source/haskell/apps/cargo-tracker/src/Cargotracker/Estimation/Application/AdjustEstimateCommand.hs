{- | 経路条件調整・再算出コマンド (US10, IT8 ストレッチ)

業務フロー (受入基準):

1. EstimateId で既存見積を取得 (不在は EstimateNotFound) — 現条件の確認は
   Interfaces 層が取得済み Estimate を表示する
2. `adjustConditions` (純粋) で到着期限・貨物種別を変更
3. Routing BC の `executeText` (Text-DTO、Rule 4 準拠) で経路候補を再算出
4. DTO → RouteCandidate に変換し `replaceCandidates` で差し替え、永続化
5. 調整後も候補 0 件の場合は Right (空候補) を返し、Interfaces 層が
   「営業担当者との条件協議」への導線を提示する (受入基準 4)

estimatedCost は Estimation BC に料金算出責務がないため 0 とする
(見積金額は US01 の料金概算フローが担う。IT8 時点の割り切り)。
-}
module Cargotracker.Estimation.Application.AdjustEstimateCommand
  ( AdjustEstimateInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime, diffUTCTime)

import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate (..),
    adjustConditions,
    replaceCandidates,
  )
import Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate,
    mkRouteCandidate,
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateId (mkEstimateId)
import Cargotracker.Routing.Application.ComputeRouteCandidatesQuery
  ( ComputeRouteCandidatesInput (..),
    RouteCandidateDto (..),
  )
import qualified Cargotracker.Routing.Application.ComputeRouteCandidatesQuery as ComputeRoutes
import Cargotracker.Routing.Application.Ports (VoyageRepository)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data AdjustEstimateInput = AdjustEstimateInput
  { inputEstimateId :: !Text
  , inputNewDeadline :: !UTCTime
  , inputNewCargoType :: !Text
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  EstimateRepository m ->
  VoyageRepository m ->
  AdjustEstimateInput ->
  m (Either DomainError Estimate)
execute repo voyageRepo input =
  case mkEstimateId (inputEstimateId input) of
    Left err -> pure (Left err)
    Right eid -> do
      mEstimate <- findEstimateById repo eid
      case mEstimate of
        Nothing -> pure (Left (EstimateNotFound (inputEstimateId input)))
        Just est ->
          case adjustConditions (inputNewDeadline input) (inputNewCargoType input) est of
            Left err -> pure (Left err)
            Right adjusted -> recompute adjusted
  where
    recompute adjusted = do
      found <-
        ComputeRoutes.executeText
          voyageRepo
          ComputeRouteCandidatesInput
            { inputOrigin = origin adjusted
            , inputDestination = destination adjusted
            , inputDeadline = deadline adjusted
            }
      case found of
        Left err -> pure (Left err)
        Right dtos ->
          case traverse dtoToCandidate dtos of
            Left err -> pure (Left err)
            Right candidates ->
              case replaceCandidates candidates adjusted of
                Left err -> pure (Left err)
                Right final -> do
                  saved <- saveEstimate repo final
                  case saved of
                    Left err -> pure (Left err)
                    Right () -> pure (Right final)

dtoToCandidate :: RouteCandidateDto -> Either DomainError RouteCandidate
dtoToCandidate dto =
  mkRouteCandidate
    (dtoRank dto)
    (transitDaysOf dto)
    0 -- estimatedCost は Estimation BC の責務外 (モジュールコメント参照)
    (dtoVoyageNumbers dto)
  where
    transitDaysOf d =
      let secs = realToFrac (diffUTCTime (dtoLastArrival d) (dtoFirstDeparture d)) :: Double
       in max 1 (ceiling (secs / 86400))
