-- | ConfirmBookingCommand のテスト (US13, IT4)
module Booking.Application.ConfirmBookingCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.ConfirmBookingCommand
  ( ConfirmBookingInput (..),
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

routeAssignedCargo :: Cargo
routeAssignedCargo =
  case submitBooking draftCargo >>= requestRouting >>= linkRoute of
    Right c -> c
    Left e -> error ("test setup: " <> show e)

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
spec = describe "ConfirmBookingCommand (US13 / IT4)" $ do
  it "RouteAssigned 状態の予約は Confirmed に遷移し updateBooking が呼ばれる" $ do
    (repo, getUpdated) <- makeRepo (Just routeAssignedCargo)
    result <- execute repo (ConfirmBookingInput bid)
    case result of
      Right c -> cargoStatus c `shouldBe` Confirmed
      Left e -> expectationFailure ("expected Right but got " <> show e)
    mUpdated <- getUpdated
    case mUpdated of
      Just c -> cargoStatus c `shouldBe` Confirmed
      Nothing -> expectationFailure "updateBooking was not called"

  it "予約が見つからない場合は BookingNotFound" $ do
    (repo, _) <- makeRepo Nothing
    result <- execute repo (ConfirmBookingInput bid)
    result `shouldBe` Left (BookingNotFound "BK-A1B2C3")

  it "Draft 状態からの confirm は InvalidStateTransition で updateBooking は呼ばれない" $ do
    (repo, getUpdated) <- makeRepo (Just draftCargo)
    result <- execute repo (ConfirmBookingInput bid)
    case result of
      Left (InvalidStateTransition "Draft" "Confirmed") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing

  it "Confirmed 状態からの再 confirm は InvalidStateTransition" $ do
    let confirmedCargo = routeAssignedCargo {cargoStatus = Confirmed}
    (repo, getUpdated) <- makeRepo (Just confirmedCargo)
    result <- execute repo (ConfirmBookingInput bid)
    case result of
      Left (InvalidStateTransition "Confirmed" "Confirmed") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing
