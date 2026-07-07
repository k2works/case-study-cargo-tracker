{-# LANGUAGE OverloadedStrings #-}

{- | ManualUpdatePageApi の hspec-wai 統合テスト (US17 5.5, IT7 / T7-A, IT8)

T7-A: RoleGate 配線後の認可マトリクスを含む。

- Cookie なし → 401
- Handler ロール → 403 (canManualStateUpdate は Tracker/MasterAdmin のみ)
- Tracker ロール → 200/303 (業務処理へ)
- changedBy は認証済み UserId が使われる (固定 "system" の廃止)
-}
module Tracking.Interfaces.ManualUpdatePageApiSpec (spec) where

import qualified Data.ByteString.Lazy as BSL
import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Time (UTCTime (..), fromGregorian, getCurrentTime, secondsToDiffTime)
import qualified Network.HTTP.Types as HTTP
import qualified Network.Wai
import qualified Network.Wai.Test as WaiTest
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository (..))
import Cargotracker.Shared.Auth.Domain.Session (Session (..))
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken, mkSessionToken)
import Cargotracker.Shared.Auth.Domain.User (Role (..), UserId (..))
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
  ( TrackingStateAudit (..),
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

-- | 44 文字英数のテスト用トークン (mkSessionToken の形式検証を通す)。
trackerToken :: T.Text
trackerToken = T.replicate 44 "a"

handlerToken :: T.Text
handlerToken = T.replicate 44 "b"

farFuture :: UTCTime
farFuture = UTCTime (fromGregorian 2099 1 1) (secondsToDiffTime 0)

mkTok :: T.Text -> SessionToken
mkTok = either (error . show) id . mkSessionToken

{- | fake SessionRepository: tracker/handler の 2 セッションを保持。
ロール解決は UserId で分岐する。
-}
fakeSessionRepo :: SessionRepository IO
fakeSessionRepo =
  SessionRepository
    { saveSession = \_ -> pure (Right ())
    , findByToken = \tok ->
        pure $
          if tok == mkTok trackerToken
            then Just (Session (mkTok trackerToken) (UserId "tracker-1") farFuture farFuture)
            else
              if tok == mkTok handlerToken
                then Just (Session (mkTok handlerToken) (UserId "handler-1") farFuture farFuture)
                else Nothing
    , deleteByToken = \_ -> pure (Right ())
    , touchLastUsed = \_ -> pure (Right ())
    }

lookupRolesFake :: UserId -> IO [Role]
lookupRolesFake (UserId "tracker-1") = pure [Tracker]
lookupRolesFake (UserId "handler-1") = pure [Handler]
lookupRolesFake _ = pure []

makeAppWithAudit :: [TrackingActivity] -> IO (Network.Wai.Application, IO [TrackingStateAudit])
makeAppWithAudit initial = do
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
          , findAuditsByTrackingNumber = \_ -> readIORef auditRef
          }
      app =
        manualUpdateApp
          trackingRepo
          auditRepo
          fakeSessionRepo
          lookupRolesFake
          getCurrentTime
  pure (app, readIORef auditRef)

makeApp :: [TrackingActivity] -> IO Network.Wai.Application
makeApp = fmap fst . makeAppWithAudit

-- | Tracker Cookie 付きで POST /tracking/TR000001/manual-update を実行する。
runPostAsTracker :: Network.Wai.Application -> IO ()
runPostAsTracker app = do
  resp <-
    WaiTest.runSession
      ( WaiTest.srequest
          WaiTest.SRequest
            { WaiTest.simpleRequest =
                WaiTest.setPath
                  WaiTest.defaultRequest
                    { Network.Wai.requestMethod = "POST"
                    , Network.Wai.requestHeaders =
                        [ ("Content-Type", "application/x-www-form-urlencoded")
                        , ("Cookie", "cargo_session=" <> TE.encodeUtf8 trackerToken)
                        ]
                    }
                  "/tracking/TR000001/manual-update"
            , WaiTest.simpleRequestBody = "newStatus=TsClaimed&reason=fix"
            }
      )
      app
  WaiTest.simpleStatus resp `shouldBe` HTTP.status303

spec :: Spec
spec = describe "ManualUpdatePageApi (US17 5.5, IT7 / T7-A RoleGate, IT8)" $ do
  describe "GET /tracking/:tn/manual-update" $
    with (makeApp [sampleActivity]) $ do
      it "Tracker Cookie で 200 とフォームを返す" $
        request
          "GET"
          "/tracking/TR000001/manual-update"
          [("Cookie", "cargo_session=" <> TE.encodeUtf8 trackerToken)]
          ""
          `shouldRespondWith` 200 {matchBody = bodyContainsText "状態を手動更新"}

      it "Cookie なしは 401" $
        get "/tracking/TR000001/manual-update"
          `shouldRespondWith` 401

      it "Handler Cookie は 403" $
        request
          "GET"
          "/tracking/TR000001/manual-update"
          [("Cookie", "cargo_session=" <> TE.encodeUtf8 handlerToken)]
          ""
          `shouldRespondWith` 403

  describe "POST /tracking/:tn/manual-update" $ do
    with (makeApp [sampleActivity]) $
      it "Tracker Cookie の正常入力で 303 リダイレクトを /public/tracking/:tn に返す" $
        request
          "POST"
          "/tracking/TR000001/manual-update"
          [ ("Content-Type", "application/x-www-form-urlencoded")
          , ("Cookie", "cargo_session=" <> TE.encodeUtf8 trackerToken)
          ]
          "newStatus=TsClaimed&reason=OK"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/public/tracking/TR000001"]
            }

    with (makeApp [sampleActivity]) $
      it "Cookie なしの POST は 401 (業務処理に到達しない)" $
        request
          "POST"
          "/tracking/TR000001/manual-update"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "newStatus=TsClaimed&reason=OK"
          `shouldRespondWith` 401

    with (makeApp [sampleActivity]) $
      it "Handler Cookie の POST は 403 (canManualStateUpdate)" $
        request
          "POST"
          "/tracking/TR000001/manual-update"
          [ ("Content-Type", "application/x-www-form-urlencoded")
          , ("Cookie", "cargo_session=" <> TE.encodeUtf8 handlerToken)
          ]
          "newStatus=TsClaimed&reason=OK"
          `shouldRespondWith` 403

    with (makeApp [sampleActivity]) $
      it "Tracker Cookie でも同一状態への遷移は 422 を返す (StateAlreadyMatches)" $
        request
          "POST"
          "/tracking/TR000001/manual-update"
          [ ("Content-Type", "application/x-www-form-urlencoded")
          , ("Cookie", "cargo_session=" <> TE.encodeUtf8 trackerToken)
          ]
          "newStatus=TsNotReceived&reason=noop"
          `shouldRespondWith` 422

  describe "changedBy の認証ユーザー連動 (T7-A)" $
    it "監査ログの changedBy が認証済み UserId になる" $ do
      (app, readAudits) <- makeAppWithAudit [sampleActivity]
      _ <-
        runPostAsTracker app
      audits <- readAudits
      map tsaChangedBy audits `shouldBe` ["tracker-1"]
