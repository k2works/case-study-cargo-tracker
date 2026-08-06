{-# LANGUAGE OverloadedStrings #-}

{- | QueryTrackingByNumberQuery の単体テスト (T5-08, IT6)

観点:
- 存在する TrackingNumber を照会すると DTO が返る
- 存在しない場合は TrackingActivityNotFound
- 形式不正の TrackingNumber は InvalidTrackingNumberFormat
- Status Text と TransportStatus が整合
-}
module Tracking.Application.QueryTrackingByNumberQuerySpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Application.QueryTrackingByNumberQuery
  ( TrackingView (..),
    execute,
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity (TrackingActivity (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( unsafeTrackingNumber,
  )

sampleTn :: T.Text
sampleTn = "TR12345A"

sampleActivity :: TrackingActivity
sampleActivity =
  TrackingActivity
    { taTrackingNumber = unsafeTrackingNumber sampleTn
    , taBookingId = "BK-A1B2C3"
    , taTransportStatus = TsOnboardCarrier
    , taVersion = 4
    }

emptyRepo :: TrackingRepository IO
emptyRepo =
  TrackingRepository
    { saveTracking = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure Nothing
    , findByTrackingNumber = \_ -> pure Nothing
    , updateTransportStatus = \_ _ -> pure (Right ())
    }

spec :: Spec
spec = describe "QueryTrackingByNumberQuery.execute (T5-08)" $ do
  it "存在する追跡番号を検索すると Right (Just TrackingView) を返す" $ do
    let repo = emptyRepo {findByTrackingNumber = \_ -> pure (Just sampleActivity)}
    result <- execute repo sampleTn
    result `shouldSatisfy` \case
      Right (Just v) ->
        tvTrackingNumber v == sampleTn
          && tvBookingId v == "BK-A1B2C3"
          && tvStatus v == TsOnboardCarrier
          && tvStatusText v == "TsOnboardCarrier"
      _ -> False

  it "存在しない追跡番号は Right Nothing (404 に相当)" $ do
    result <- execute emptyRepo sampleTn
    result `shouldBe` Right Nothing

  it "形式不正の追跡番号は Left InvalidTrackingNumberFormat" $ do
    result <- execute emptyRepo "short"
    result `shouldBe` Left (InvalidTrackingNumberFormat "short")
