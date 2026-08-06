{-# LANGUAGE OverloadedStrings #-}

-- | AttachCustomsDeclarationCommand のテスト (US27, IT3)
module Booking.Application.AttachCustomsDeclarationCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.AttachCustomsDeclarationCommand
  ( AttachCustomsInput (..),
    execute,
  )
import Cargotracker.Booking.Application.CustomsPorts
  ( CustomsDeclarationRepository (..),
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo,
    mkCargo,
  )
import Cargotracker.Booking.Domain.Model.CustomsDeclaration
  ( CustomsDeclaration (..),
  )
import Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( DeclarationStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.HsCode (unHsCode)
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

baseRoute :: RouteSpecification
baseRoute =
  RouteSpecification
    { origin = UnLocode "JPTYO"
    , destination = UnLocode "USNYC"
    , arrivalDeadline = deadline
    }

sampleCargo :: Cargo
sampleCargo = mkCargo bid (ShipperRef "SHP-X1Y2Z3") baseRoute

makeBookingRepo :: Maybe Cargo -> BookingRepository IO
makeBookingRepo seed =
  BookingRepository
    { saveBooking = \_ -> pure (Right ())
    , findCargoById = \_ -> pure seed
    , updateBooking = \_ -> pure (Right ())
    , findAllCargos = pure []
    }

makeCustomsRepo ::
  IO (CustomsDeclarationRepository IO, IO (Maybe CustomsDeclaration))
makeCustomsRepo = do
  ref <- newIORef Nothing
  let repo =
        CustomsDeclarationRepository
          { upsertCustomsDeclaration = \cd -> do
              writeIORef ref (Just cd)
              pure (Right ())
          , findByBookingId = \_ -> readIORef ref
          }
  pure (repo, readIORef ref)

baseInput :: AttachCustomsInput
baseInput =
  AttachCustomsInput
    { inputBookingId = bid
    , inputHsCode = "123456"
    , inputBrokerName = "ABC 通関"
    , inputStatusText = "PENDING"
    }

spec :: Spec
spec = describe "AttachCustomsDeclarationCommand (US27)" $ do
  it "正常系: 予約存在 + 検証成功で Right + upsert 呼び出し" $ do
    (customsRepo, getStored) <- makeCustomsRepo
    res <- execute (makeBookingRepo (Just sampleCargo)) customsRepo baseInput
    case res of
      Right cd -> do
        cdStatus cd `shouldBe` Pending
        unHsCode (cdHsCode cd) `shouldBe` "123456"
      Left e -> expectationFailure ("expected Right but got " <> show e)
    stored <- getStored
    case stored of
      Just cd -> unHsCode (cdHsCode cd) `shouldBe` "123456"
      Nothing -> expectationFailure "upsert was not called"

  it "予約未存在は Left BookingNotFound で upsert は呼ばれない" $ do
    (customsRepo, getStored) <- makeCustomsRepo
    res <- execute (makeBookingRepo Nothing) customsRepo baseInput
    res `shouldBe` Left (BookingNotFound "BK-A1B2C3")
    stored <- getStored
    stored `shouldBe` Nothing

  it "HS コード形式不正は Left InvalidHsCode で upsert は呼ばれない" $ do
    (customsRepo, getStored) <- makeCustomsRepo
    let bad = baseInput {inputHsCode = "BAD"}
    res <- execute (makeBookingRepo (Just sampleCargo)) customsRepo bad
    res `shouldBe` Left (InvalidHsCode "BAD")
    stored <- getStored
    stored `shouldBe` Nothing

  it "不正なステータス文字列は Left InvalidDeclarationStatus" $ do
    (customsRepo, _) <- makeCustomsRepo
    let bad = baseInput {inputStatusText = "FOO"}
    res <- execute (makeBookingRepo (Just sampleCargo)) customsRepo bad
    res `shouldBe` Left (InvalidDeclarationStatus "FOO")
