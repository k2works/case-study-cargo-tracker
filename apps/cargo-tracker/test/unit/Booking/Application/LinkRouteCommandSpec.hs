-- | LinkRouteCommand のテスト (US11, IT4)
module Booking.Application.LinkRouteCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.LinkRouteCommand
  ( LinkRouteInput (..),
    execute,
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

routeProposedCargo :: Cargo
routeProposedCargo = case submitBooking draftCargo >>= requestRouting of
  Right c -> c
  Left e -> error ("setup: " <> show e)

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
spec = describe "LinkRouteCommand (US11 / IT4)" $ do
  it "RouteProposed 状態の予約は RouteAssigned に遷移" $ do
    (repo, getUpdated) <- makeRepo (Just routeProposedCargo)
    result <- execute repo (LinkRouteInput bid)
    case result of
      Right c -> cargoStatus c `shouldBe` RouteAssigned
      Left e -> expectationFailure ("expected Right but got " <> show e)
    mUpdated <- getUpdated
    case mUpdated of
      Just c -> cargoStatus c `shouldBe` RouteAssigned
      Nothing -> expectationFailure "updateBooking was not called"

  it "予約が見つからない場合は BookingNotFound" $ do
    (repo, _) <- makeRepo Nothing
    result <- execute repo (LinkRouteInput bid)
    result `shouldBe` Left (BookingNotFound "BK-A1B2C3")

  it "Draft 状態からの linkRoute は InvalidStateTransition で updateBooking は呼ばれない" $ do
    (repo, getUpdated) <- makeRepo (Just draftCargo)
    result <- execute repo (LinkRouteInput bid)
    case result of
      Left (InvalidStateTransition "Draft" "RouteAssigned") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing
