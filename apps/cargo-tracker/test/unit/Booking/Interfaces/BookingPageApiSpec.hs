{-# LANGUAGE OverloadedStrings #-}

{- | BookingPageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /bookings/new がフォーム入力を受け取り、成功時は予約詳細画面 (`/bookings/:bookingId`)、
失敗時 (荷主未存在 / 期限フォーマット不正など) はフォーム + `?error=` クエリへ
リダイレクト (303 See Other) することを検証する。
-}
module Booking.Interfaces.BookingPageApiSpec (spec) where

import Data.IORef (modifyIORef', newIORef)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo (Cargo)
import Cargotracker.Booking.Interfaces.BookingPageApi (bookingPageApp)
import Network.Wai (Application)

makeRepo :: IO (BookingRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Cargo])
  pure
    BookingRepository
      { saveBooking = \c -> do
          modifyIORef' ref (c :)
          pure (Right ())
      , findCargoById = \_ -> pure Nothing
      }

checkerYes :: ShipperExistenceChecker IO
checkerYes = ShipperExistenceChecker {exists = \_ -> pure True}

checkerNo :: ShipperExistenceChecker IO
checkerNo = ShipperExistenceChecker {exists = \_ -> pure False}

mkApp :: ShipperExistenceChecker IO -> IO Application
mkApp ch = do
  repo <- makeRepo
  pure (bookingPageApp repo ch)

spec :: Spec
spec = do
  describe "POST /bookings/new (T-03 PRG / 荷主あり)" $
    with (mkApp checkerYes) $ do
      it "正常系は 303 を返し Location が /bookings/:bookingId を指す" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=BK-A1B2C3&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=2026-12-31T00%3A00"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/BK-A1B2C3"]
            }

      it "期限フォーマット不正は 303 + Location=/bookings/new?error=deadline-format" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=BK-A1B2C3&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=BAD"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/new?error=deadline-format"]
            }

  describe "POST /bookings/new (T-03 PRG / 荷主なし)" $
    with (mkApp checkerNo) $ do
      it "ShipperNotFound は 303 + Location=/bookings/new?error=shipper-not-found" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=BK-A1B2C3&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=2026-12-31T00%3A00"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/new?error=shipper-not-found"]
            }
