{-# LANGUAGE OverloadedStrings #-}

-- | RouteFinder のテスト (US08a, IT3 タスク 4.1)
module Routing.Domain.Service.RouteFinderSpec (spec) where

import Data.Text (Text)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage
  ( Voyage,
    mkVoyage,
  )
import Cargotracker.Routing.Domain.Service.RouteFinder
  ( FoundRoute (..),
    findRoutes,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))

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
  Right v -> case mkVoyage v ms of
    Right voy -> voy
    Left e -> error (show e)
  Left e -> error (show e)

spec :: Spec
spec = describe "RouteFinder (US08a)" $ do
  let origin = UnLocode "JPTYO"
      destination = UnLocode "USNYC"
      deadline = day 30

  it "直行便のみのとき rank=0 で 1 件返る" $ do
    let direct = mkV "V0001" [cm "JPTYO" "USNYC" t0 (day 14)]
        result = findRoutes origin destination deadline [direct]
    length result `shouldBe` 1
    frRank (head result) `shouldBe` 0
    frNumSegments (head result) `shouldBe` 1
    map unVoyageNumber (frVoyageNumbers (head result)) `shouldBe` ["V0001"]

  it "乗継ぎ便 JPTYO -> SGSIN -> USNYC が検出される" $ do
    let leg1 = mkV "V1" [cm "JPTYO" "SGSIN" t0 (day 7)]
        leg2 = mkV "V2" [cm "SGSIN" "USNYC" (day 8) (day 21)]
        result = findRoutes origin destination deadline [leg1, leg2]
    length result `shouldBe` 1
    frNumSegments (head result) `shouldBe` 2
    map unVoyageNumber (frVoyageNumbers (head result)) `shouldBe` ["V1", "V2"]

  it "直行便と経由便が両方ある場合、直行便が rank=0、経由便が rank=1" $ do
    let direct = mkV "VD" [cm "JPTYO" "USNYC" t0 (day 14)]
        leg1 = mkV "VT1" [cm "JPTYO" "SGSIN" t0 (day 7)]
        leg2 = mkV "VT2" [cm "SGSIN" "USNYC" (day 8) (day 21)]
        result = findRoutes origin destination deadline [direct, leg1, leg2]
    length result `shouldBe` 2
    frRank (head result) `shouldBe` 0
    map unVoyageNumber (frVoyageNumbers (head result)) `shouldBe` ["VD"]
    frRank (result !! 1) `shouldBe` 1

  it "期限超過の経路は除外される" $ do
    let lateDirect = mkV "V0001" [cm "JPTYO" "USNYC" t0 (day 40)]
        result = findRoutes origin destination deadline [lateDirect]
    result `shouldBe` []

  it "出発地と接続しない Voyage は無視される" $ do
    let unrelated = mkV "V0001" [cm "USLAX" "GBLON" t0 (day 14)]
        result = findRoutes origin destination deadline [unrelated]
    result `shouldBe` []

  it "目的地と接続しない経由便は除外される" $ do
    let leg1 = mkV "V1" [cm "JPTYO" "SGSIN" t0 (day 7)]
        wrong = mkV "V2" [cm "SGSIN" "GBLON" (day 8) (day 21)]
        result = findRoutes origin destination deadline [leg1, wrong]
    result `shouldBe` []

  it "結果は最大 5 件に制限される" $ do
    let mkOne i = mkV (textOf i) [cm "JPTYO" "USNYC" t0 (day (5 + i))]
        textOf i = "V" <> packInt i
        packInt i = case i of
          1 -> "01"
          2 -> "02"
          3 -> "03"
          4 -> "04"
          5 -> "05"
          6 -> "06"
          7 -> "07"
          _ -> "0X"
        voys = map mkOne [1 .. 7]
        result = findRoutes origin destination deadline voys
    length result `shouldBe` 5

  it "接続時刻が前便より早い (待合せ不能) 経由便は除外される" $ do
    let leg1 = mkV "V1" [cm "JPTYO" "SGSIN" (day 10) (day 17)]
        -- V2 の出発が V1 の到着 (day 17) より早い (day 8) ので接続不可
        leg2 = mkV "V2" [cm "SGSIN" "USNYC" (day 8) (day 21)]
        result = findRoutes origin destination deadline [leg1, leg2]
    result `shouldBe` []
