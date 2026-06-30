-- | UnlinkRouteCommand のテスト (US11, IT4)
module Booking.Application.UnlinkRouteCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Application.UnlinkRouteCommand
  ( UnlinkRouteInput (..),
    execute,
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
    Left e -> error ("setup: " <> show e)

confirmedCargo :: Cargo
confirmedCargo = routeAssignedCargo {cargoStatus = Confirmed}

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
spec = describe "UnlinkRouteCommand (US11 / IT4)" $ do
  it "RouteAssigned 状態は Draft に戻る (確定前なら解除可能)" $ do
    (repo, getUpdated) <- makeRepo (Just routeAssignedCargo)
    result <- execute repo (UnlinkRouteInput bid)
    case result of
      Right c -> cargoStatus c `shouldBe` Draft
      Left e -> expectationFailure (show e)
    mUpdated <- getUpdated
    case mUpdated of
      Just c -> cargoStatus c `shouldBe` Draft
      Nothing -> expectationFailure "updateBooking was not called"

  it "予約が見つからない場合は BookingNotFound" $ do
    (repo, _) <- makeRepo Nothing
    result <- execute repo (UnlinkRouteInput bid)
    result `shouldBe` Left (BookingNotFound "BK-A1B2C3")

  it "Confirmed 状態からの unlinkRoute は InvalidStateTransition" $ do
    (repo, getUpdated) <- makeRepo (Just confirmedCargo)
    result <- execute repo (UnlinkRouteInput bid)
    case result of
      Left (InvalidStateTransition "Confirmed" "Draft") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing

  it "Draft 状態からの unlinkRoute は InvalidStateTransition (二重解除防止)" $ do
    (repo, _) <- makeRepo (Just draftCargo)
    result <- execute repo (UnlinkRouteInput bid)
    case result of
      Left (InvalidStateTransition "Draft" "Draft") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
