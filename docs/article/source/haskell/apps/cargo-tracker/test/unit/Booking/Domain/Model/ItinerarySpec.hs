-- | Itinerary + Leg + ItineraryId のテスト (US09, IT4)
module Booking.Domain.Model.ItinerarySpec (spec) where

import Data.Either (fromRight, isLeft, isRight)
import qualified Data.List.NonEmpty as NE
import Data.Text (Text)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Itinerary
  ( itArrivalTime,
    itDepartureTime,
    itPorts,
    mkItinerary,
  )
import Cargotracker.Booking.Domain.Model.Leg (Leg, mkLeg)
import Cargotracker.Booking.Domain.Model.Value.ItineraryId
  ( ItineraryId,
    mkItineraryId,
    unItineraryId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

iid :: ItineraryId
iid = fromRight (error "bad id") (mkItineraryId "550e8400-e29b-41d4-a716-446655440000")

t :: Int -> Int -> Int -> UTCTime
t y m d = UTCTime (fromGregorian (fromIntegral y) m d) (secondsToDiffTime 0)

mkLegU :: Int -> Text -> Text -> UTCTime -> UTCTime -> Text -> Leg
mkLegU n a b lt ut v = fromRight (error "bad leg") (mkLeg n a b lt ut v)

spec :: Spec
spec = describe "Itinerary / Leg / ItineraryId (US09 / IT4)" $ do
  describe "ItineraryId" $ do
    it "UUID v4 形式 (36 文字、8-4-4-4-12 区切り) を受理" $
      mkItineraryId "550e8400-e29b-41d4-a716-446655440000" `shouldSatisfy` isRight

    it "35 文字 (1 文字欠落) は拒否" $
      mkItineraryId "550e8400-e29b-41d4-a716-44665544000"
        `shouldBe` Left (InvalidItineraryId "550e8400-e29b-41d4-a716-44665544000")

    it "ハイフン区切り違反 (8-4-5-4-11) は拒否" $
      mkItineraryId "550e8400-e29b-41d4a-a716-44665544000" `shouldSatisfy` isLeft

    it "unItineraryId で元の文字列を復元" $
      fmap unItineraryId (mkItineraryId "550e8400-e29b-41d4-a716-446655440000")
        `shouldBe` Right "550e8400-e29b-41d4-a716-446655440000"

  describe "Leg.mkLeg" $ do
    it "正常: 順序・空文字違反なし" $
      mkLeg 1 "JPTYO" "USNYC" (t 2026 9 1) (t 2026 9 20) "V001" `shouldSatisfy` isRight

    it "seq_number=0 は拒否" $
      mkLeg 0 "JPTYO" "USNYC" (t 2026 9 1) (t 2026 9 20) "V001" `shouldSatisfy` isLeft

    it "load_time >= unload_time は拒否" $
      mkLeg 1 "JPTYO" "USNYC" (t 2026 9 20) (t 2026 9 1) "V001" `shouldSatisfy` isLeft

    it "voyage_number 空は拒否" $
      mkLeg 1 "JPTYO" "USNYC" (t 2026 9 1) (t 2026 9 20) "" `shouldSatisfy` isLeft

  describe "Itinerary.mkItinerary" $ do
    it "1 区間の直行便を受理" $
      mkItinerary
        iid
        (mkLegU 1 "JPTYO" "USNYC" (t 2026 9 1) (t 2026 9 20) "V001" NE.:| [])
        `shouldSatisfy` isRight

    it "2 区間で接続性 OK (JPTYO->SGSIN, SGSIN->USNYC) を受理" $ do
      let leg1 = mkLegU 1 "JPTYO" "SGSIN" (t 2026 9 1) (t 2026 9 8) "V001"
          leg2 = mkLegU 2 "SGSIN" "USNYC" (t 2026 9 10) (t 2026 9 25) "V002"
      mkItinerary iid (leg1 NE.:| [leg2]) `shouldSatisfy` isRight

    it "接続違反 (leg1.unload=SGSIN != leg2.load=HKHKG) は拒否" $ do
      let leg1 = mkLegU 1 "JPTYO" "SGSIN" (t 2026 9 1) (t 2026 9 8) "V001"
          leg2 = mkLegU 2 "HKHKG" "USNYC" (t 2026 9 10) (t 2026 9 25) "V002"
      mkItinerary iid (leg1 NE.:| [leg2]) `shouldSatisfy` isLeft

    it "時刻順序違反 (leg1.unload=9-20 > leg2.load=9-10) は拒否" $ do
      let leg1 = mkLegU 1 "JPTYO" "SGSIN" (t 2026 9 1) (t 2026 9 20) "V001"
          leg2 = mkLegU 2 "SGSIN" "USNYC" (t 2026 9 10) (t 2026 9 25) "V002"
      mkItinerary iid (leg1 NE.:| [leg2]) `shouldSatisfy` isLeft

    it "seq_number 非連番 (1, 3) は拒否" $ do
      let leg1 = mkLegU 1 "JPTYO" "SGSIN" (t 2026 9 1) (t 2026 9 8) "V001"
          leg3 = mkLegU 3 "SGSIN" "USNYC" (t 2026 9 10) (t 2026 9 25) "V002"
      mkItinerary iid (leg1 NE.:| [leg3]) `shouldSatisfy` isLeft

  describe "Itinerary 派生フィールド" $
    it "itDepartureTime / itArrivalTime / itPorts (2 区間)" $ do
      let leg1 = mkLegU 1 "JPTYO" "SGSIN" (t 2026 9 1) (t 2026 9 8) "V001"
          leg2 = mkLegU 2 "SGSIN" "USNYC" (t 2026 9 10) (t 2026 9 25) "V002"
          it_ = fromRight (error "bad it") (mkItinerary iid (leg1 NE.:| [leg2]))
      itDepartureTime it_ `shouldBe` t 2026 9 1
      itArrivalTime it_ `shouldBe` t 2026 9 25
      itPorts it_ `shouldBe` ["JPTYO", "SGSIN", "USNYC"]
