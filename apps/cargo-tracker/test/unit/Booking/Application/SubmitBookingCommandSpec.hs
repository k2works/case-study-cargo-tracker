-- | SubmitBookingCommand のテスト (US06 補完, IT3 / H-03)
module Booking.Application.SubmitBookingCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Application.SubmitBookingCommand
  ( SubmitBookingInput (..),
    execute,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
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
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))

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
draftCargo = mkCargo bid (ShipperId "SHP-X1Y2Z3") baseRoute

submittedCargo :: Cargo
submittedCargo = case submitBooking draftCargo of
  Right c -> c
  Left _ -> error "test setup: submitBooking failed"

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
spec = describe "SubmitBookingCommand (H-03)" $ do
  it "Draft 状態の予約は Submitted に遷移し updateBooking が呼ばれる" $ do
    (repo, getUpdated) <- makeRepo (Just draftCargo)
    result <- execute repo (SubmitBookingInput bid)
    case result of
      Right c -> cargoStatus c `shouldBe` Submitted
      Left e -> expectationFailure ("expected Right but got " <> show e)
    mUpdated <- getUpdated
    case mUpdated of
      Just c -> cargoStatus c `shouldBe` Submitted
      Nothing -> expectationFailure "updateBooking was not called"

  it "予約が見つからない場合は BookingNotFound" $ do
    (repo, _) <- makeRepo Nothing
    result <- execute repo (SubmitBookingInput bid)
    result `shouldBe` Left (BookingNotFound "BK-A1B2C3")

  it "Submitted 状態からの再 submit は InvalidStateTransition で updateBooking は呼ばれない" $ do
    (repo, getUpdated) <- makeRepo (Just submittedCargo)
    result <- execute repo (SubmitBookingInput bid)
    case result of
      Left (InvalidStateTransition "Submitted" "Submitted") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing
