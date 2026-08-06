{-# LANGUAGE OverloadedStrings #-}

-- | PostgresBookingRepository / PostgresShipperExistenceChecker の統合テスト
module Booking.Infrastructure.PostgresBookingRepositorySpec (spec) where

import qualified Data.ByteString.Char8 as BC
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    close,
    connectPostgreSQL,
    execute,
    query,
  )
import System.Environment (lookupEnv)
import Test.Hspec

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo (mkCargo)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Infrastructure.PostgresBookingRepository
  ( newPostgresBookingRepository,
  )
import Cargotracker.Booking.Infrastructure.PostgresShipperExistenceChecker
  ( newPostgresShipperExistenceChecker,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

deadline :: UTCTime
deadline = UTCTime (fromGregorian 2027 1 31) (secondsToDiffTime 0)

withDb :: (Connection -> IO ()) -> IO ()
withDb = error "unused" -- 単純化のため使わない

setupShipper :: Connection -> IO ()
setupShipper conn = do
  _ <- execute conn "DELETE FROM cargo WHERE booking_id = ?" (Only ("BK-INT001" :: String))
  _ <- execute conn "DELETE FROM shipper WHERE shipper_id = ?" (Only ("SHP-INT001" :: String))
  _ <-
    execute
      conn
      "INSERT INTO shipper (shipper_id, name, email, address, shipper_kind, version) \
      \ VALUES (?, ?, ?, ?, ?, 1)"
      ( "SHP-INT001" :: String
      , "integ-shipper-test" :: String
      , "integ-shipper-test@example.com" :: String
      , "test address" :: String
      , "Individual" :: String
      )
  pure ()

teardownShipper :: Connection -> IO ()
teardownShipper conn = do
  _ <- execute conn "DELETE FROM cargo WHERE booking_id = ?" (Only ("BK-INT001" :: String))
  _ <- execute conn "DELETE FROM shipper WHERE shipper_id = ?" (Only ("SHP-INT001" :: String))
  pure ()

spec :: Spec
spec = describe "PostgresBookingRepository [INTEGRATION]" $ do
  it "存在する荷主の予約を保存できる" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing -> pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        setupShipper conn
        let repo = newPostgresBookingRepository conn
            route =
              RouteSpecification
                { origin = UnLocode "JPTYO"
                , destination = UnLocode "USNYC"
                , arrivalDeadline = deadline
                }
            cargo =
              mkCargo
                (BookingId "BK-INT001")
                (ShipperRef "SHP-INT001")
                route
        saveResult <- saveBooking repo cargo
        saveResult `shouldBe` Right ()
        -- 検証: cargo テーブルから直接 SELECT
        rows <-
          query
            conn
            "SELECT booking_id, booking_status FROM cargo WHERE booking_id = ?"
            (Only ("BK-INT001" :: String)) ::
            IO [(String, String)]
        rows `shouldBe` [("BK-INT001", "Draft")]
        teardownShipper conn
        close conn

  describe "PostgresShipperExistenceChecker [INTEGRATION]" $ do
    it "存在する荷主は True" $ do
      mUrl <- lookupEnv "DATABASE_URL"
      case mUrl of
        Nothing -> pendingWith "DATABASE_URL not set; skipped"
        Just url -> do
          conn <- connectPostgreSQL (BC.pack url)
          setupShipper conn
          let checker = newPostgresShipperExistenceChecker conn
          result <- exists checker (ShipperRef "SHP-INT001")
          result `shouldBe` True
          teardownShipper conn
          close conn

    it "存在しない荷主は False" $ do
      mUrl <- lookupEnv "DATABASE_URL"
      case mUrl of
        Nothing -> pendingWith "DATABASE_URL not set; skipped"
        Just url -> do
          conn <- connectPostgreSQL (BC.pack url)
          let checker = newPostgresShipperExistenceChecker conn
          result <- exists checker (ShipperRef "SHP-NOEXIST")
          result `shouldBe` False
          close conn
