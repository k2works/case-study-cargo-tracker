-- | HandOverToRouterCommand のテスト (US06, IT2)
module Booking.Application.HandOverToRouterCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.HandOverToRouterCommand
  ( HandOverToRouterInput (..),
    execute,
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
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

-- Repository フェイク: 任意の Maybe Cargo を返し、updated は IORef に書き出す
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
spec = describe "HandOverToRouterCommand" $ do
  it "Submitted 状態の予約は RouteProposed に遷移し updateBooking が呼ばれる" $ do
    (repo, getUpdated) <- makeRepo (Just submittedCargo)
    result <- execute repo (HandOverToRouterInput bid)
    case result of
      Right c -> cargoStatus c `shouldBe` RouteProposed
      Left e -> expectationFailure ("expected Right but got " <> show e)
    mUpdated <- getUpdated
    case mUpdated of
      Just c -> cargoStatus c `shouldBe` RouteProposed
      Nothing -> expectationFailure "updateBooking was not called"

  it "予約が見つからない場合は BookingNotFound" $ do
    (repo, _) <- makeRepo Nothing
    result <- execute repo (HandOverToRouterInput bid)
    result `shouldBe` Left (BookingNotFound "BK-A1B2C3")

  it "Draft 状態からの直接引き渡しは InvalidStateTransition で updateBooking は呼ばれない" $ do
    (repo, getUpdated) <- makeRepo (Just draftCargo)
    result <- execute repo (HandOverToRouterInput bid)
    case result of
      Left (InvalidStateTransition "Draft" "RouteProposed") -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing

  it "Repository.updateBooking が ConcurrentModification を返したら伝播する" $ do
    updRef <- newIORef Nothing
    let repo =
          BookingRepository
            { saveBooking = \_ -> pure (Right ())
            , findCargoById = \_ -> pure (Just submittedCargo)
            , updateBooking = \c -> do
                writeIORef updRef (Just c)
                pure (Left (ConcurrentModification "BK-A1B2C3"))
            , findAllCargos = pure []
            }
    result <- execute repo (HandOverToRouterInput bid)
    result `shouldBe` Left (ConcurrentModification "BK-A1B2C3")
