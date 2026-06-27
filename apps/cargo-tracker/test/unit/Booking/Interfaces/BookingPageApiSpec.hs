{-# LANGUAGE OverloadedStrings #-}

{- | BookingPageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /bookings/new がフォーム入力を受け取り、成功時は予約詳細画面 (`/bookings/:bookingId`)、
失敗時 (荷主未存在 / 期限フォーマット不正など) はフォーム + `?error=` クエリへ
リダイレクト (303 See Other) することを検証する。
-}
module Booking.Interfaces.BookingPageApiSpec (spec) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Data.IORef (modifyIORef', newIORef)
import Network.HTTP.Types.Header (Header)
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchHeader (..))

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo,
    mkCargo,
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Interfaces.BookingPageApi (bookingPageApp)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Network.Wai (Application)

-- T-07 (IT2): サーバ採番された ID を含む Location の接頭辞一致検証
matchLocationPrefix :: BS.ByteString -> MatchHeader
matchLocationPrefix prefix = MatchHeader $ \hs _ ->
  case lookup "Location" (hs :: [Header]) of
    Just v | prefix `BS.isPrefixOf` v -> Nothing
    Just v -> Just ("Location does not start with " <> show prefix <> ": got " <> show v)
    Nothing -> Just "missing Location header"

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

mkApp :: ShipperExistenceChecker IO -> IO Application
mkApp ch = do
  repo <- makeRepo
  pure (bookingPageApp repo ch)

-- US06 (IT2): /handover テスト用に、特定の Cargo を find で返し、
-- updateBooking 呼出を IORef に記録する Repository を作る。
deadlineAt :: UTCTime
deadlineAt = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

routeForHandover :: RouteSpecification
routeForHandover =
  RouteSpecification
    { origin = UnLocode "JPTYO"
    , destination = UnLocode "USNYC"
    , arrivalDeadline = deadlineAt
    }

submittedCargo :: Cargo
submittedCargo =
  let draft = mkCargo (BookingId "BK-A1B2C3") (ShipperId "SHP-X1Y2Z3") routeForHandover
   in case submitBooking draft of
        Right c -> c
        Left _ -> error "test setup: submitBooking failed"

draftCargo :: Cargo
draftCargo = mkCargo (BookingId "BK-A1B2C3") (ShipperId "SHP-X1Y2Z3") routeForHandover

mkHandoverApp :: Maybe Cargo -> IO Application
mkHandoverApp seed = do
  let repo =
        BookingRepository
          { saveBooking = \_ -> pure (Right ())
          , findCargoById = \_ -> pure seed
          , updateBooking = \_ -> pure (Right ())
          }
  pure (bookingPageApp repo checkerYes)

spec :: Spec
spec = do
  describe "POST /bookings/new (T-03 PRG / 荷主あり)" $
    with (mkApp checkerYes) $ do
      it "正常系は 303 を返し Location が /bookings/BK-... を指す (T-07 自動採番)" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=IGNORED&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=2026-12-31T00%3A00"
          `shouldRespondWith` 303
            { matchHeaders = [matchLocationPrefix "/bookings/BK-"]
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

  describe "POST /bookings/:bookingId/handover (US06 PRG)" $ do
    with (mkHandoverApp (Just submittedCargo)) $
      it "Submitted 状態は 303 を返し flash=handover-ok を付ける" $
        request "POST" "/bookings/BK-A1B2C3/handover" [] ""
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/bookings/BK-A1B2C3?flash=handover-ok"]
            }

    with (mkHandoverApp (Just draftCargo)) $
      it "Draft 状態は 303 + invalid-state エラーを付ける" $
        request "POST" "/bookings/BK-A1B2C3/handover" [] ""
          `shouldRespondWith` 303
            { matchHeaders =
                [ "Location"
                    <:> "/bookings/BK-A1B2C3?error=invalid-state&from=Draft"
                ]
            }

    with (mkHandoverApp Nothing) $
      it "予約未存在は 303 + /bookings/new?error=booking-not-found を指す" $
        request "POST" "/bookings/BK-NOTHERE/handover" [] ""
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/bookings/new?error=booking-not-found"]
            }

  describe "GET /bookings/new (T-08 フラッシュ表示)" $
    with (mkApp checkerYes) $ do
      it "?error なしは 200 を返す" $
        get "/bookings/new" `shouldRespondWith` 200

      it "?error=deadline-format で 200 + 期限フォーマットエラーを含む" $ do
        res <- get "/bookings/new?error=deadline-format"
        liftIO $ do
          let body = LBS.toStrict (simpleBody res)
          shouldSatisfy
            body
            (\b -> "\xe5\x88\xb0\xe7\x9d\x80\xe6\x9c\x9f\xe9\x99\x90" `BS.isInfixOf` b)
