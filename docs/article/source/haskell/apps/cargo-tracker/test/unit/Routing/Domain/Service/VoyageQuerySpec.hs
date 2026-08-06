{-# LANGUAGE OverloadedStrings #-}

-- | VoyageQuery のテスト (US07, IT3)
module Routing.Domain.Service.VoyageQuerySpec (spec) where

import Data.Text (Text)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement (CarrierMovement (..))
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber,
    mkVoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageSearchCriteria
  ( VoyageSearchCriteria (..),
    mkVoyageSearchCriteria,
  )
import Cargotracker.Routing.Domain.Model.Voyage
  ( Voyage,
    mkVoyage,
    voyageNumber,
  )
import Cargotracker.Routing.Domain.Service.VoyageQuery
  ( matchesCriteria,
    sortByDeparture,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 8 1) (secondsToDiffTime 0)

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
spec = describe "VoyageQuery (US07)" $ do
  let directV =
        mkV
          "V0001"
          [cm "JPTYO" "USNYC" t0 (addUTCTime 86400 t0)]
      transitV =
        mkV
          "V0002"
          [ cm "JPTYO" "SGSIN" t0 (addUTCTime 86400 t0)
          , cm "SGSIN" "USNYC" (addUTCTime 90000 t0) (addUTCTime (2 * 86400) t0)
          ]
      otherV =
        mkV
          "V0003"
          [cm "JPOSA" "GBLON" t0 (addUTCTime 86400 t0)]

  describe "mkVoyageSearchCriteria" $ do
    it "出発期間の from > to は InvalidSearchPeriod を返す" $ do
      let from = addUTCTime 86400 t0
          to = t0
      mkVoyageSearchCriteria (UnLocode "JPTYO") (UnLocode "USNYC") from to
        `shouldBe` Left (InvalidSearchPeriod from to)

    it "出発地と目的地が同じ場合は SameOriginDestination" $ do
      let same = UnLocode "JPTYO"
      mkVoyageSearchCriteria same same t0 (addUTCTime 86400 t0)
        `shouldBe` Left (SameOriginDestination "JPTYO")

    it "正常な条件は Right" $ do
      let from = t0
          to = addUTCTime 86400 t0
      case mkVoyageSearchCriteria (UnLocode "JPTYO") (UnLocode "USNYC") from to of
        Right c -> vscOrigin c `shouldBe` UnLocode "JPTYO"
        Left e -> expectationFailure ("expected Right but got " <> show e)

  describe "matchesCriteria" $ do
    let validCrit =
          either (error . show) id $
            mkVoyageSearchCriteria
              (UnLocode "JPTYO")
              (UnLocode "USNYC")
              t0
              (addUTCTime 86400 t0)

    it "直行便 JPTYO -> USNYC は条件にマッチする" $
      matchesCriteria validCrit directV `shouldBe` True

    it "経由便 JPTYO -> SGSIN -> USNYC も条件にマッチする (最終到着が目的地)" $
      matchesCriteria validCrit transitV `shouldBe` True

    it "別経路 JPOSA -> GBLON はマッチしない" $
      matchesCriteria validCrit otherV `shouldBe` False

    it "出発期間外の航海はマッチしない" $ do
      let futureCrit =
            either (error . show) id $
              mkVoyageSearchCriteria
                (UnLocode "JPTYO")
                (UnLocode "USNYC")
                (addUTCTime (10 * 86400) t0)
                (addUTCTime (20 * 86400) t0)
      matchesCriteria futureCrit directV `shouldBe` False

  describe "sortByDeparture" $
    it "最初の区間の出発時刻昇順にソートされる" $ do
      let v1 = mkV "V0010" [cm "JPTYO" "USNYC" (addUTCTime 86400 t0) (addUTCTime (2 * 86400) t0)]
          v2 = mkV "V0011" [cm "JPTYO" "USNYC" t0 (addUTCTime 86400 t0)]
          v3 = mkV "V0012" [cm "JPTYO" "USNYC" (addUTCTime (3 * 86400) t0) (addUTCTime (4 * 86400) t0)]
          sorted = sortByDeparture [v1, v2, v3]
      map (unVoyageNumber . voyageNumber) sorted `shouldBe` ["V0011", "V0010", "V0012"]
