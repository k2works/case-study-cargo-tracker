-- | CancelBookingCommand のテスト (US13, IT4)
module Booking.Application.CancelBookingCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Ratio ((%))
import Data.Time
  ( UTCTime (..),
    addUTCTime,
    fromGregorian,
    secondsToDiffTime,
  )
import Test.Hspec

import Cargotracker.Booking.Application.CancelBookingCommand
  ( BookingDepartureContext (..),
    CancelBookingInput (..),
    CancelBookingResult (..),
    execute,
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    linkRoute,
    mkCargo,
    requestRouting,
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId (..),
  )
import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
  )
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

deadline :: UTCTime
deadline = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

baseRoute :: RouteSpecification
baseRoute =
  RouteSpecification
    { origin = UnLocode "JPTYO"
    , destination = UnLocode "USNYC"
    , arrivalDeadline = deadline
    }

bid :: BookingId
bid = BookingId "BK-A1B2C3"

draftCargo :: Cargo
draftCargo = mkCargo bid (ShipperRef "SHP-X1Y2Z3") baseRoute

submittedCargo :: Cargo
submittedCargo = case submitBooking draftCargo of
  Right c -> c
  Left e -> error ("setup: " <> show e)

confirmedCargo :: Cargo
confirmedCargo =
  case submitBooking draftCargo >>= requestRouting >>= linkRoute of
    Right c -> c {cargoStatus = Confirmed}
    Left e -> error ("setup: " <> show e)

departure :: UTCTime
departure = UTCTime (fromGregorian 2026 9 10) (secondsToDiffTime (12 * 3600))

now7Days :: UTCTime
now7Days = addUTCTime (negate (7 * 24 * 3600)) departure

now5Days :: UTCTime
now5Days = addUTCTime (negate (5 * 24 * 3600)) departure

now6Hours :: UTCTime
now6Hours = addUTCTime (negate (6 * 3600)) departure

makeRepo :: Maybe Cargo -> IO (BookingRepository IO, IO (Maybe Cargo))
makeRepo seed = do
  updRef <- newIORef (Nothing :: Maybe Cargo)
  let r =
        BookingRepository
          { saveBooking = \_ -> pure (Right ())
          , findCargoById = \_ -> pure seed
          , updateBooking = \c -> do
              writeIORef updRef (Just c)
              pure (Right ())
          , findAllCargos = pure []
          }
  pure (r, readIORef updRef)

spec :: Spec
spec = describe "CancelBookingCommand (US13 / IT4)" $ do
  it "Submitted 状態のキャンセルは料金 Free (出航日不要)" $ do
    (repo, _) <- makeRepo (Just submittedCargo)
    result <-
      execute
        repo
        (CancelBookingInput bid now7Days NoDeparture)
    case result of
      Right r -> do
        cargoStatus (resultCargo r) `shouldBe` Cancelled
        cfTier (resultFee r) `shouldBe` Free
        cfRate (resultFee r) `shouldBe` (0 % 100)
      Left e -> expectationFailure (show e)

  it "Confirmed + 出航 7 日前 → Free / 0%" $ do
    (repo, _) <- makeRepo (Just confirmedCargo)
    result <-
      execute
        repo
        (CancelBookingInput bid now7Days (HasDeparture departure))
    case result of
      Right r -> do
        cargoStatus (resultCargo r) `shouldBe` Cancelled
        cfTier (resultFee r) `shouldBe` Free
      Left e -> expectationFailure (show e)

  it "Confirmed + 出航 5 日前 → Partial / 30%" $ do
    (repo, _) <- makeRepo (Just confirmedCargo)
    result <-
      execute
        repo
        (CancelBookingInput bid now5Days (HasDeparture departure))
    case result of
      Right r -> do
        cargoStatus (resultCargo r) `shouldBe` Cancelled
        cfTier (resultFee r) `shouldBe` Partial
        cfRate (resultFee r) `shouldBe` (30 % 100)
      Left e -> expectationFailure (show e)

  it "Confirmed + 出航 6 時間前 → Full / 100%" $ do
    (repo, _) <- makeRepo (Just confirmedCargo)
    result <-
      execute
        repo
        (CancelBookingInput bid now6Hours (HasDeparture departure))
    case result of
      Right r -> do
        cargoStatus (resultCargo r) `shouldBe` Cancelled
        cfTier (resultFee r) `shouldBe` Full
        cfRate (resultFee r) `shouldBe` (100 % 100)
      Left e -> expectationFailure (show e)

  it "予約が見つからない場合は BookingNotFound" $ do
    (repo, _) <- makeRepo Nothing
    result <-
      execute
        repo
        (CancelBookingInput bid now7Days NoDeparture)
    case result of
      Left (BookingNotFound "BK-A1B2C3") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)

  it "Draft からのキャンセルは InvalidStateTransition" $ do
    (repo, getUpdated) <- makeRepo (Just draftCargo)
    result <-
      execute
        repo
        (CancelBookingInput bid now7Days NoDeparture)
    case result of
      Left (InvalidStateTransition "Draft" "Cancelled") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing
