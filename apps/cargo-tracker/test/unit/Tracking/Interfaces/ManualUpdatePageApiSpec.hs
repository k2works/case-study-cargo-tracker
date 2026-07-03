{-# LANGUAGE OverloadedStrings #-}

-- | ManualUpdatePageApi の hspec-wai 統合テスト (US17 5.5, IT7)
module Tracking.Interfaces.ManualUpdatePageApiSpec (spec) where

import qualified Data.ByteString.Lazy as BSL
import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Application.TrackingStateAuditPorts
  ( TrackingStateAuditRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    initialActivity,
  )
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit
  ( TrackingStateAudit,
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (mkTrackingNumber)
import Cargotracker.Tracking.Interfaces.ManualUpdatePageApi (manualUpdateApp)

bodyContainsText :: T.Text -> MatchBody
bodyContainsText needle =
  MatchBody $ \_ body ->
    let bodyText = TE.decodeUtf8 (BSL.toStrict body)
     in if needle `T.isInfixOf` bodyText
          then Nothing
          else Just ("body does not contain " <> T.unpack needle)

sampleActivity :: TrackingActivity
sampleActivity = case mkTrackingNumber "TR000001" of
  Right tn -> initialActivity tn "BK-A1B2C3"
  Left _ -> error "test setup: invalid tracking number"

makeApp :: [TrackingActivity] -> IO Network.Wai.Application
makeApp initial = do
  activityRef <- newIORef initial
  auditRef <- newIORef ([] :: [TrackingStateAudit])
  let trackingRepo =
        TrackingRepository
          { saveTracking = \_ -> pure (Right ())
          , findByBookingId = \bid -> do
              xs <- readIORef activityRef
              pure (case [a | a <- xs, taBookingId a == bid] of (x : _) -> Just x; [] -> Nothing)
          , findByTrackingNumber = \tn -> do
              xs <- readIORef activityRef
              pure (case [a | a <- xs, taTrackingNumber a == tn] of (x : _) -> Just x; [] -> Nothing)
          , updateTransportStatus = \bid st -> do
              modifyIORef'
                activityRef
                (map (\a -> if taBookingId a == bid then a {taTransportStatus = st} else a))
              pure (Right ())
          }
      auditRepo =
        TrackingStateAuditRepository
          { saveAudit = \a -> do
              modifyIORef' auditRef (a :)
              pure (Right ())
          }
  pure (manualUpdateApp trackingRepo auditRepo "tracker-1")

spec :: Spec
spec = describe "ManualUpdatePageApi (US17 5.5, IT7)" $ do
  describe "GET /tracking/:tn/manual-update" $
    with (makeApp [sampleActivity]) $ do
      it "200 とフォームを返す" $
        get "/tracking/TR000001/manual-update"
          `shouldRespondWith` 200 {matchBody = bodyContainsText "状態を手動更新"}

      it "追跡番号をフォームに含む" $
        get "/tracking/TR000001/manual-update"
          `shouldRespondWith` 200 {matchBody = bodyContainsText "TR000001"}

  describe "POST /tracking/:tn/manual-update" $ do
    with (makeApp [sampleActivity]) $
      it "正常入力で 303 リダイレクトを /public/tracking/:tn に返す" $
        request
          "POST"
          "/tracking/TR000001/manual-update"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "newStatus=TsClaimed&reason=OK"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/public/tracking/TR000001"]
            }

    with (makeApp [sampleActivity]) $
      it "同一状態への遷移は 422 を返す (StateAlreadyMatches)" $
        request
          "POST"
          "/tracking/TR000001/manual-update"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "newStatus=TsNotReceived&reason=noop"
          `shouldRespondWith` 422
