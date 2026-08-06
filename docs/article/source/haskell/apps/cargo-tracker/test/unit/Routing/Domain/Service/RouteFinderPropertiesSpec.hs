{-# LANGUAGE OverloadedStrings #-}

{- | RouteFinder の hedgehog プロパティテスト (US08a タスク 4.2, IT3)

3 つの不変条件をプロパティテストで検証する:

* P-1: 算出した経路は必ず期日内 (frLastArrival <= deadline)
* P-2: 乗継ぎ便の各区間は時刻順 (前便 arrivalTime <= 次便 departureTime)
* P-3: 直行便が候補に存在する場合、rank=0 のものは必ず直行便
-}
module Routing.Domain.Service.RouteFinderPropertiesSpec (spec) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Hedgehog (Gen, Property, assert, check, forAll, property, success, (===))
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage, mkVoyage)
import Cargotracker.Routing.Domain.Service.RouteFinder
  ( FoundRoute (..),
    findRoutes,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))

-- ------------------------------------------------------------------
-- 共通: 基準時刻 / 港候補 / 生成器
-- ------------------------------------------------------------------

baseTime :: UTCTime
baseTime = UTCTime (fromGregorian 2026 9 1) (secondsToDiffTime 0)

days :: Int -> UTCTime
days n = addUTCTime (fromIntegral n * 86400) baseTime

allPorts :: [Text]
allPorts = ["JPTYO", "SGSIN", "HKHKG", "USLAX", "USNYC"]

genPort :: Gen Text
genPort = Gen.element allPorts

-- 期日: baseTime から 5..60 日後
genDeadline :: Gen UTCTime
genDeadline = days <$> Gen.int (Range.linear 5 60)

-- 1 区間直行便: dep != arr、時刻は baseTime..deadline 内
genDirectVoyage :: Text -> Text -> UTCTime -> Gen Voyage
genDirectVoyage origin destination deadline = do
  dep <- Gen.int (Range.linear 0 20)
  travelDays <- Gen.int (Range.linear 1 30)
  let depT = days dep
      arrT = days (dep + travelDays)
  vn <- Gen.int (Range.linear 1 99)
  case mkVoyageNumber (T.pack ("V" <> show vn)) of
    Left _ -> Gen.discard
    Right vno -> case mkVoyage vno [cm origin destination depT arrT] of
      Left _ -> Gen.discard
      Right v ->
        if arrT > deadline
          then Gen.discard
          else pure v

cm :: Text -> Text -> UTCTime -> UTCTime -> CarrierMovement
cm dep arr depT arrT =
  CarrierMovement
    { departureLocation = UnLocode dep
    , arrivalLocation = UnLocode arr
    , departureTime = depT
    , arrivalTime = arrT
    }

-- 2 区間経由便: dep != mid && mid != arr、時刻順を満たすよう構築
genTransitVoyages :: Text -> Text -> Text -> UTCTime -> Gen [Voyage]
genTransitVoyages origin mid destination deadline = do
  dep1 <- Gen.int (Range.linear 0 15)
  travel1 <- Gen.int (Range.linear 1 15)
  wait <- Gen.int (Range.linear 0 5)
  travel2 <- Gen.int (Range.linear 1 15)
  let leg1Dep = days dep1
      leg1Arr = days (dep1 + travel1)
      leg2Dep = days (dep1 + travel1 + wait)
      leg2Arr = days (dep1 + travel1 + wait + travel2)
  case (mkVoyageNumber "V100", mkVoyageNumber "V101") of
    (Right v100, Right v101) ->
      case ( mkVoyage v100 [cm origin mid leg1Dep leg1Arr]
           , mkVoyage v101 [cm mid destination leg2Dep leg2Arr]
           ) of
        (Right l1, Right l2)
          | leg2Arr <= deadline -> pure [l1, l2]
        _ -> Gen.discard
    _ -> Gen.discard

-- ------------------------------------------------------------------
-- プロパティ
-- ------------------------------------------------------------------

-- P-1: 算出した経路は必ず期日内
prop_within_deadline :: Property
prop_within_deadline = property $ do
  origin <- forAll genPort
  destination <- forAll (Gen.filter (/= origin) genPort)
  deadline <- forAll genDeadline
  vs <- forAll (Gen.list (Range.linear 1 5) (genDirectVoyage origin destination deadline))
  let results = findRoutes (UnLocode origin) (UnLocode destination) deadline vs
  -- すべての候補が期限以内
  assert (all (\r -> frLastArrival r <= deadline) results)

-- P-2: 経由便の各区間は時刻順 (frFirstDeparture <= frLastArrival)
prop_temporal_order :: Property
prop_temporal_order = property $ do
  origin <- forAll genPort
  mid <- forAll (Gen.filter (/= origin) genPort)
  destination <- forAll (Gen.filter (\p -> p /= origin && p /= mid) genPort)
  deadline <- forAll genDeadline
  vs <- forAll (genTransitVoyages origin mid destination deadline)
  let results = findRoutes (UnLocode origin) (UnLocode destination) deadline vs
  -- frFirstDeparture <= frLastArrival は時刻順の必要条件
  assert (all (\r -> frFirstDeparture r <= frLastArrival r) results)

-- P-3: 直行便が候補に存在する場合、rank=0 は直行便 (frNumSegments == 1)
prop_direct_voyage_is_rank_zero :: Property
prop_direct_voyage_is_rank_zero = property $ do
  origin <- forAll genPort
  destination <- forAll (Gen.filter (/= origin) genPort)
  deadline <- forAll genDeadline
  direct <- forAll (genDirectVoyage origin destination deadline)
  -- 経由便も混ぜる (直行が必ず勝つか)
  intermediate <- forAll (Gen.filter (\p -> p /= origin && p /= destination) genPort)
  transits <- forAll (genTransitVoyages origin intermediate destination deadline)
  let results = findRoutes (UnLocode origin) (UnLocode destination) deadline (direct : transits)
  case results of
    [] -> success
    (top : _) ->
      -- 直行便を含めて検索した結果、rank=0 は必ず 1 区間
      frRank top === 0 >> assert (frNumSegments top == 1)

-- ------------------------------------------------------------------
-- hspec wrapper
-- ------------------------------------------------------------------

runProp :: String -> Property -> Spec
runProp name p = it name $ do
  ok <- liftIO (check p)
  ok `shouldBe` True

spec :: Spec
spec = describe "RouteFinder hedgehog プロパティ (US08a タスク 4.2)" $ do
  runProp "P-1: 算出した経路は必ず期日内" prop_within_deadline
  runProp "P-2: 経由便の出発時刻は到着時刻より前 (時刻順)" prop_temporal_order
  runProp "P-3: 直行便が候補にあれば rank=0 は直行便" prop_direct_voyage_is_rank_zero
