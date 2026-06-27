{-# LANGUAGE OverloadedStrings #-}

{- | 貨物予約 API のテスト (IT1 US04 3.5)

POST /bookings で {bookingId, shipperId, origin, destination, deadline}
を受け取り、RegisterBookingCommand 経由で集約を生成・保存する。

- 201 Created: 正常登録
- 422: バリデーション失敗
- 404 Not Found: 荷主未存在 (ShipperNotFound)
- 400: JSON パース失敗
-}
module Booking.Interfaces.BookingApiSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.IORef (modifyIORef', newIORef, readIORef)
import Network.HTTP.Types (methodPost)
import Network.Wai (Application)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo (Cargo)
import Cargotracker.Booking.Interfaces.BookingApi (bookingApp)

makeRepo :: IO (BookingRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Cargo])
  pure
    BookingRepository
      { saveBooking = \c -> do
          modifyIORef' ref (c :)
          pure (Right ())
      , findCargoById = \_ -> pure Nothing
      , updateBooking = \_ -> pure (Right ())
      }

checkerYes :: ShipperExistenceChecker IO
checkerYes = ShipperExistenceChecker {exists = \_ -> pure True}

checkerNo :: ShipperExistenceChecker IO
checkerNo = ShipperExistenceChecker {exists = \_ -> pure False}

testAppYes :: IO Application
testAppYes = do
  repo <- makeRepo
  pure (bookingApp repo checkerYes)

testAppNo :: IO Application
testAppNo = do
  repo <- makeRepo
  pure (bookingApp repo checkerNo)

validBody :: LBC.ByteString
validBody =
  "{\"bookingId\":\"BK-A1B2C3\",\"shipperId\":\"SHP-X1Y2Z3\","
    <> "\"origin\":\"JPTYO\",\"destination\":\"USNYC\","
    <> "\"deadline\":\"2026-12-31T00:00:00Z\"}"

invalidIdBody :: LBC.ByteString
invalidIdBody =
  "{\"bookingId\":\"WRONG\",\"shipperId\":\"SHP-X1Y2Z3\","
    <> "\"origin\":\"JPTYO\",\"destination\":\"USNYC\","
    <> "\"deadline\":\"2026-12-31T00:00:00Z\"}"

spec :: Spec
spec = do
  describe "POST /bookings (with valid shipper)" $
    with testAppYes $ do
      it "正常な入力は 201" $
        request methodPost "/bookings" [("Content-Type", "application/json")] validBody
          `shouldRespondWith` 201

      it "BookingId が不正なら 422" $
        request methodPost "/bookings" [("Content-Type", "application/json")] invalidIdBody
          `shouldRespondWith` 422

      it "壊れた JSON は 400" $
        request methodPost "/bookings" [("Content-Type", "application/json")] "not-json"
          `shouldRespondWith` 400

  describe "POST /bookings (with non-existent shipper)" $
    with testAppNo $
      it "荷主未存在は 404" $
        request methodPost "/bookings" [("Content-Type", "application/json")] validBody
          `shouldRespondWith` 404
