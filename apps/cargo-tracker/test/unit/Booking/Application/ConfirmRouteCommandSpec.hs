-- | ConfirmRouteCommand のテスト (US09, IT4)
module Booking.Application.ConfirmRouteCommandSpec (spec) where

import Data.Either (fromRight)
import Data.IORef (newIORef, readIORef, writeIORef)
import qualified Data.List.NonEmpty as NE
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.ConfirmRouteCommand
  ( ConfirmRouteInput (..),
    ConfirmRouteResult (..),
    execute,
  )
import Cargotracker.Booking.Application.ItineraryPorts
  ( ItineraryRepository (..),
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
    requestRouting,
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.Itinerary (Itinerary, itLegs)
import Cargotracker.Booking.Domain.Model.Leg (Leg, mkLeg)
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId (..),
  )
import Cargotracker.Booking.Domain.Model.Value.ItineraryId
  ( ItineraryId,
    mkItineraryId,
  )
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

deadline :: UTCTime
deadline = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

bid :: BookingId
bid = BookingId "BK-A1B2C3"

iid :: ItineraryId
iid = fromRight (error "iid") (mkItineraryId "550e8400-e29b-41d4-a716-446655440000")

baseRoute :: RouteSpecification
baseRoute =
  RouteSpecification
    { origin = UnLocode "JPTYO"
    , destination = UnLocode "USNYC"
    , arrivalDeadline = deadline
    }

draftCargo :: Cargo
draftCargo = mkCargo bid (ShipperRef "SHP-X1Y2Z3") baseRoute

routeProposedCargo :: Cargo
routeProposedCargo = case submitBooking draftCargo >>= requestRouting of
  Right c -> c
  Left e -> error ("setup: " <> show e)

t :: Int -> UTCTime
t d = UTCTime (fromGregorian 2026 9 d) (secondsToDiffTime 0)

leg1, leg2 :: Leg
leg1 = fromRight (error "leg1") (mkLeg 1 "JPTYO" "SGSIN" (t 1) (t 8) "V001")
leg2 = fromRight (error "leg2") (mkLeg 2 "SGSIN" "USNYC" (t 10) (t 25) "V002")

makeRepos ::
  Maybe Cargo ->
  IO
    ( BookingRepository IO
    , ItineraryRepository IO
    , IO (Maybe Cargo)
    , IO (Maybe Itinerary)
    )
makeRepos seed = do
  cargoRef <- newIORef (Nothing :: Maybe Cargo)
  itinRef <- newIORef (Nothing :: Maybe Itinerary)
  let bookingR =
        BookingRepository
          { saveBooking = \_ -> pure (Right ())
          , findCargoById = \_ -> pure seed
          , updateBooking = \c -> do
              writeIORef cargoRef (Just c)
              pure (Right ())
          , findAllCargos = pure []
          }
      itinR =
        ItineraryRepository
          { saveItinerary = \_ it -> do
              writeIORef itinRef (Just it)
              pure (Right ())
          , findItineraryByBookingId = \_ -> pure Nothing
          , findItineraryById = \_ -> pure Nothing
          }
  pure (bookingR, itinR, readIORef cargoRef, readIORef itinRef)

spec :: Spec
spec = describe "ConfirmRouteCommand (US09 / IT4)" $ do
  it "RouteProposed + 妥当な Itinerary は RouteAssigned に遷移し両 Repository が更新される" $ do
    (bookingR, itinR, getCargo, getItin) <- makeRepos (Just routeProposedCargo)
    result <-
      execute
        bookingR
        itinR
        ConfirmRouteInput
          { inputBookingId = bid
          , inputItineraryId = iid
          , inputLegs = leg1 NE.:| [leg2]
          }
    case result of
      Right r -> do
        cargoStatus (resultCargo r) `shouldBe` RouteAssigned
        NE.length (itLegs (resultItinerary r)) `shouldBe` 2
      Left e -> expectationFailure (show e)
    mC <- getCargo
    case mC of
      Just c -> cargoStatus c `shouldBe` RouteAssigned
      Nothing -> expectationFailure "cargo not updated"
    mI <- getItin
    case mI of
      Just _ -> pure ()
      Nothing -> expectationFailure "itinerary not saved"

  it "予約が見つからない場合は BookingNotFound" $ do
    (bookingR, itinR, _, _) <- makeRepos Nothing
    result <-
      execute
        bookingR
        itinR
        ConfirmRouteInput
          { inputBookingId = bid
          , inputItineraryId = iid
          , inputLegs = leg1 NE.:| [leg2]
          }
    result `shouldSatisfy` (\case Left (BookingNotFound _) -> True; _ -> False)

  it "Draft 状態からの確定は InvalidStateTransition (linkRoute 失敗で itinerary は保存されない)" $ do
    (bookingR, itinR, getCargo, getItin) <- makeRepos (Just draftCargo)
    result <-
      execute
        bookingR
        itinR
        ConfirmRouteInput
          { inputBookingId = bid
          , inputItineraryId = iid
          , inputLegs = leg1 NE.:| [leg2]
          }
    result `shouldSatisfy` (\case Left (InvalidStateTransition "Draft" "RouteAssigned") -> True; _ -> False)
    mC <- getCargo
    mC `shouldBe` Nothing
    mI <- getItin
    mI `shouldBe` Nothing

  it "Leg 接続性違反は InvalidItinerary (cargo/itinerary とも保存されない)" $ do
    (bookingR, itinR, getCargo, getItin) <- makeRepos (Just routeProposedCargo)
    let badLeg2 = fromRight (error "badLeg2") (mkLeg 2 "HKHKG" "USNYC" (t 10) (t 25) "V002")
    result <-
      execute
        bookingR
        itinR
        ConfirmRouteInput
          { inputBookingId = bid
          , inputItineraryId = iid
          , inputLegs = leg1 NE.:| [badLeg2]
          }
    result `shouldSatisfy` (\case Left (InvalidItinerary _) -> True; _ -> False)
    mC <- getCargo
    mC `shouldBe` Nothing
    mI <- getItin
    mI `shouldBe` Nothing
