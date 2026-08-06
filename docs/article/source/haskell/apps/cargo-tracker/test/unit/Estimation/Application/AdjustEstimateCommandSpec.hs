{-# LANGUAGE OverloadedStrings #-}

{- | AdjustEstimateCommand のテスト (US10, IT8 ストレッチ)

受入基準に対応:

- 条件調整 (期限延長・貨物種別変更) → Routing BC 再算出 → 候補差し替え・永続化
- 期限延長で候補が増える (期限内到達可能になる) ことを検証
- 調整後も候補 0 件は Right (空候補) — 営業への条件協議導線は Interfaces 層
- 見積不在は EstimateNotFound、空の貨物種別は InvalidRouteAdjustment
-}
module Estimation.Application.AdjustEstimateCommandSpec (spec) where

import Control.Monad (void)
import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Text (Text)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Estimation.Application.AdjustEstimateCommand
  ( AdjustEstimateInput (..),
  )
import qualified Cargotracker.Estimation.Application.AdjustEstimateCommand as Adjust
import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate (..),
    mkEstimate,
  )
import Cargotracker.Estimation.Domain.Model.RouteCandidate (RouteCandidate (..))
import Cargotracker.Estimation.Domain.Model.Value.EstimateId (mkEstimateId)
import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (mkVoyageNumber)
import Cargotracker.Routing.Domain.Model.Voyage (Voyage, mkVoyage)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 9 1) (secondsToDiffTime 0)

day :: Integer -> UTCTime
day n = addUTCTime (fromIntegral n * 86400) t0

cm :: Text -> Text -> UTCTime -> UTCTime -> CarrierMovement
cm dep arr depT arrT =
  CarrierMovement
    { departureLocation = UnLocode dep
    , arrivalLocation = UnLocode arr
    , departureTime = depT
    , arrivalTime = arrT
    }

mkV :: Text -> [CarrierMovement] -> Voyage
mkV vn ms = case mkVoyageNumber vn of
  Right v -> either (error . show) id (mkVoyage v ms)
  Left e -> error (show e)

-- | 14 日かかる直行便 1 本のみの世界
voyageRepo14d :: VoyageRepository IO
voyageRepo14d =
  VoyageRepository
    { findByVoyageNumber = \_ -> pure Nothing
    , saveVoyage = \_ -> pure ()
    , updateVoyage = \_ -> pure (Right ())
    , findAllVoyages = pure [mkV "VD" [cm "JPTYO" "USNYC" t0 (day 14)]]
    }

sampleEstimateId :: Text
sampleEstimateId = "550e8400-e29b-41d4-a716-446655440000"

-- | 期限 7 日 (14 日便に間に合わない) の見積、候補なし
tightEstimate :: Estimate
tightEstimate =
  either (error . show) id $
    mkEstimate
      (either (error . show) id (mkEstimateId sampleEstimateId))
      "SHP-X1Y2Z3"
      (UnLocode "JPTYO")
      (UnLocode "USNYC")
      (day 7)
      "GENERAL"
      100.0
      []

newFakeRepo :: [Estimate] -> IO (EstimateRepository IO, IO [Estimate])
newFakeRepo initial = do
  ref <- newIORef initial
  let repo =
        EstimateRepository
          { saveEstimate = \e -> do
              modifyIORef' ref ((e :) . filter ((/= estimateId e) . estimateId))
              pure (Right ())
          , findEstimateById = \eid -> do
              xs <- readIORef ref
              pure (case filter ((== eid) . estimateId) xs of (x : _) -> Just x; [] -> Nothing)
          , findAllEstimates = readIORef ref
          }
  pure (repo, readIORef ref)

adjustTo30d :: AdjustEstimateInput
adjustTo30d =
  AdjustEstimateInput
    { inputEstimateId = sampleEstimateId
    , inputNewDeadline = day 30
    , inputNewCargoType = "REFRIGERATED"
    }

spec :: Spec
spec = describe "AdjustEstimateCommand (US10, IT8)" $ do
  it "期限延長 + 貨物種別変更で再算出され候補が差し替わる" $ do
    (repo, dump) <- newFakeRepo [tightEstimate]
    r <- Adjust.execute repo voyageRepo14d adjustTo30d
    case r of
      Left e -> expectationFailure (show e)
      Right est -> do
        deadline est `shouldBe` day 30
        cargoTypeText est `shouldBe` "REFRIGERATED"
        map voyageNumbers (routeCandidates est) `shouldBe` [["VD"]]
        map transitDays (routeCandidates est) `shouldBe` [14]
    saved <- dump
    map (length . routeCandidates) saved `shouldBe` [1]

  it "調整後も期限内到達不可なら Right + 空候補 (条件協議導線へ)" $ do
    (repo, _) <- newFakeRepo [tightEstimate]
    r <-
      Adjust.execute
        repo
        voyageRepo14d
        adjustTo30d {inputNewDeadline = day 10}
    case r of
      Left e -> expectationFailure (show e)
      Right est -> routeCandidates est `shouldBe` []

  it "見積不在は EstimateNotFound" $ do
    (repo, _) <- newFakeRepo []
    r <- Adjust.execute repo voyageRepo14d adjustTo30d
    void r `shouldBe` Left (EstimateNotFound sampleEstimateId)

  it "空の貨物種別は InvalidRouteAdjustment" $ do
    (repo, _) <- newFakeRepo [tightEstimate]
    r <-
      Adjust.execute
        repo
        voyageRepo14d
        adjustTo30d {inputNewCargoType = "  "}
    void r `shouldBe` Left (InvalidRouteAdjustment "empty cargo type")

  it "不正な EstimateId 形式はエラー" $ do
    (repo, _) <- newFakeRepo [tightEstimate]
    r <-
      Adjust.execute
        repo
        voyageRepo14d
        adjustTo30d {inputEstimateId = "not-a-uuid"}
    case r of
      Left _ -> pure ()
      Right _ -> expectationFailure "expected Left for invalid estimate id"
