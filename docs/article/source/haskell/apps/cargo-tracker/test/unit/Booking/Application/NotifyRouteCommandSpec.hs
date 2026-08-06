{-# LANGUAGE OverloadedStrings #-}

{- | NotifyRouteCommand のテスト (US12, IT8 ストレッチ)

受入基準:

- 紐付け経路の通知内容 (経由港・所要日数・到着予定日・料金概算) が本文に含まれる
- 通知送信で送信記録 (saveNotification) が登録される
- 経路未紐付けは InvalidItinerary、予約不在は BookingNotFound
-}
module Booking.Application.NotifyRouteCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.List.NonEmpty as NE
import qualified Data.Text as T
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.ItineraryPorts (ItineraryRepository (..))
import Cargotracker.Booking.Application.NotifyRouteCommand
  ( NotifyRouteInput (..),
  )
import qualified Cargotracker.Booking.Application.NotifyRouteCommand as Notify
import Cargotracker.Booking.Application.Ports (BookingRepository (..))
import Cargotracker.Booking.Domain.Model.Cargo (Cargo, mkCargo)
import Cargotracker.Booking.Domain.Model.Itinerary (Itinerary, mkItinerary)
import Cargotracker.Booking.Domain.Model.Leg (mkLeg)
import Cargotracker.Booking.Domain.Model.Value.BookingId (mkBookingId)
import Cargotracker.Booking.Domain.Model.Value.ItineraryId (mkItineraryId)
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
    NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationContent (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 10 1) (secondsToDiffTime 0)

day :: Integer -> UTCTime
day n = addUTCTime (fromIntegral n * 86400) t0

sampleCargo :: Cargo
sampleCargo =
  let Right o = mkUnLocode "JPTYO"
      Right d = mkUnLocode "USNYC"
      Right bid = mkBookingId "BK-A1B2C3"
   in mkCargo bid (ShipperRef "SHP-X1Y2Z3") (RouteSpecification o d (day 30))

sampleItinerary :: Itinerary
sampleItinerary =
  let Right iid = mkItineraryId "550e8400-e29b-41d4-a716-446655440000"
      Right leg1 = mkLeg 1 "JPTYO" "SGSIN" t0 (day 7) "V001"
      Right leg2 = mkLeg 2 "SGSIN" "USNYC" (day 8) (day 14) "V002"
   in either (error . show) id (mkItinerary iid (leg1 NE.:| [leg2]))

bookingRepoWith :: [Cargo] -> BookingRepository IO
bookingRepoWith cargos =
  BookingRepository
    { saveBooking = \_ -> pure (Right ())
    , findCargoById = \bid -> pure (if null cargos then Nothing else Just (head cargos))
    , updateBooking = \_ -> pure (Right ())
    , findAllCargos = pure cargos
    }

itineraryRepoWith :: Maybe Itinerary -> ItineraryRepository IO
itineraryRepoWith mIt =
  ItineraryRepository
    { saveItinerary = \_ _ -> pure (Right ())
    , findItineraryByBookingId = \_ -> pure mIt
    , findItineraryById = \_ -> pure mIt
    }

runNotify ::
  [Cargo] ->
  Maybe Itinerary ->
  Maybe T.Text ->
  IO (Either DomainError DeliveryResult, [Notification])
runNotify cargos mIt mCost = do
  savedRef <- newIORef []
  let notifRepo =
        NotificationRepository
          { saveNotification = \n -> modifyIORef' savedRef (n :) >> pure (Right ())
          , findByBookingId = \_ -> pure []
          , updateNotification = \_ -> pure (Right ())
          }
      delivery = NotificationDeliveryPort {deliver = \_ -> pure DeliverySucceeded}
  r <-
    Notify.execute
      (bookingRepoWith cargos)
      (itineraryRepoWith mIt)
      notifRepo
      delivery
      NotifyRouteInput
        { inputBookingId = "BK-A1B2C3"
        , inputCostEstimateText = mCost
        , inputNow = t0
        , inputNotificationId = Just "uuid-1"
        }
  saved <- readIORef savedRef
  pure (r, saved)

spec :: Spec
spec = describe "NotifyRouteCommand (US12, IT8)" $ do
  it "経由港・所要日数・到着予定日・料金概算が本文に含まれ送信記録が残る" $ do
    (r, saved) <- runNotify [sampleCargo] (Just sampleItinerary) (Just "436500 JPY")
    r `shouldBe` Right DeliverySucceeded
    case saved of
      [n] -> do
        nBookingId n `shouldBe` "BK-A1B2C3"
        let body = ncBody (nContent n)
        body `shouldSatisfy` T.isInfixOf "JPTYO → SGSIN → USNYC"
        body `shouldSatisfy` T.isInfixOf "所要日数: 14 日"
        body `shouldSatisfy` T.isInfixOf "料金概算: 436500 JPY"
      _ -> expectationFailure ("expected 1 notification, got " <> show (length saved))

  it "料金未算定は「未算定」と表示される" $ do
    (_, saved) <- runNotify [sampleCargo] (Just sampleItinerary) Nothing
    map (ncBody . nContent) saved `shouldSatisfy` all (T.isInfixOf "料金概算: 未算定")

  it "経路未紐付けは InvalidItinerary で通知は記録されない" $ do
    (r, saved) <- runNotify [sampleCargo] Nothing Nothing
    r `shouldBe` Left (InvalidItinerary "no itinerary linked to booking")
    saved `shouldBe` []

  it "予約不在は BookingNotFound" $ do
    (r, saved) <- runNotify [] (Just sampleItinerary) Nothing
    r `shouldBe` Left (BookingNotFound "BK-A1B2C3")
    saved `shouldBe` []
