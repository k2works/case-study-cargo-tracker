{-# LANGUAGE OverloadedStrings #-}

{- | T7-C (IT8): handlerPost UNLOAD 分岐の副作用テスト

fake `ConfirmationCodeRepository` を IORef で spy 化し、

1. UNLOAD 登録時のみ確認コード発行 (saveConfirmationCode) が発火する
2. LOAD / RECEIVE 等では発火しない
3. 既発行 (findByBookingId = Just) なら再保存しない (冪等性)

を Postgres なしで検証する (ralph-loop-week2_review 高 #4)。
IORef の事後検査が必要なため hspec-wai ではなく Network.Wai.Test を直接使う。
-}
module Handling.Interfaces.HandlingPageApiUnloadSpec (spec) where

import qualified Data.ByteString.Lazy as BSL
import Data.IORef (IORef, modifyIORef', newIORef, readIORef)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Network.HTTP.Types (hContentType, methodPost, status303)
import Network.Wai (requestHeaders, requestMethod)
import Network.Wai.Test
  ( SRequest (..),
    defaultRequest,
    runSession,
    setPath,
    simpleHeaders,
    simpleStatus,
    srequest,
  )
import Test.Hspec

import Cargotracker.Handling.Application.Ports
  ( HandlingActivityRepository (..),
  )
import Cargotracker.Handling.Interfaces.HandlingPageApi
  ( HandlingPageDeps (..),
    handlingPageApp,
  )
import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
    NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationContent (..),
  )
import Cargotracker.Shared.Application.TxRunner (noTxRunner)
import Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository (..),
  )
import qualified Cargotracker.Tracking.Application.Ports as TrackingPorts
import Cargotracker.Tracking.Domain.Model.ConfirmationCode
  ( ConfirmationCode,
    mkConfirmationCode,
  )

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 10 12) (secondsToDiffTime 43200)

existingCode :: ConfirmationCode
existingCode =
  either (error . show) id (mkConfirmationCode sampleNow "654321")

fakeHandlingRepo :: HandlingActivityRepository IO
fakeHandlingRepo =
  HandlingActivityRepository
    { saveHandlingActivity = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure []
    }

{- | spy: saveConfirmationCode の呼び出しを IORef に記録する。
mExisting が Just なら IssueConfirmationCodeCommand は既存を返す (冪等)。
-}
spyCodeRepo ::
  IORef [Text] ->
  Maybe ConfirmationCode ->
  ConfirmationCodeRepository IO
spyCodeRepo callsRef mExisting =
  ConfirmationCodeRepository
    { saveConfirmationCode = \bid _cc -> do
        modifyIORef' callsRef (bid :)
        pure (Right ())
    , findByBookingId = \_ -> pure mExisting
    , updateAfterVerify = \_ _ -> pure (Right ())
    }

fakeTrackingRepo :: TrackingPorts.TrackingRepository IO
fakeTrackingRepo =
  TrackingPorts.TrackingRepository
    { TrackingPorts.saveTracking = \_ -> pure (Right ())
    , TrackingPorts.findByBookingId = \_ -> pure Nothing
    , TrackingPorts.findByTrackingNumber = \_ -> pure Nothing
    , TrackingPorts.updateTransportStatus = \_ _ -> pure (Right ())
    }

fakeNotifRepo :: NotificationRepository IO
fakeNotifRepo =
  NotificationRepository
    { saveNotification = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure []
    , updateNotification = \_ -> pure (Right ())
    }

-- | T7-E: 保存された通知を spy 記録する NotificationRepository
spyNotifRepo :: IORef [Notification] -> NotificationRepository IO
spyNotifRepo ref =
  NotificationRepository
    { saveNotification = \n -> modifyIORef' ref (n :) >> pure (Right ())
    , findByBookingId = \_ -> pure []
    , updateNotification = \_ -> pure (Right ())
    }

fakeDelivery :: NotificationDeliveryPort IO
fakeDelivery = NotificationDeliveryPort {deliver = \_ -> pure DeliverySucceeded}

{- | POST /handling/new をフォーム送信し、303 と spy の記録を返す。
completionTime は過去時刻 (mkHandlingActivity の未来時刻検証を通す)。
-}
postHandling :: Maybe ConfirmationCode -> Text -> IO [Text]
postHandling mExisting eventType = do
  (calls, _) <- postHandlingWith fakeNotifRepo mExisting eventType
  pure calls

postHandlingWith ::
  NotificationRepository IO ->
  Maybe ConfirmationCode ->
  Text ->
  IO ([Text], ())
postHandlingWith notifRepo mExisting eventType = do
  callsRef <- newIORef []
  let app =
        handlingPageApp
          HandlingPageDeps
            { hpdTxRunner = noTxRunner
            , hpdHandlingRepo = fakeHandlingRepo
            , hpdCodeRepo = spyCodeRepo callsRef mExisting
            , hpdTrackingRepo = fakeTrackingRepo
            , hpdNotificationRepo = notifRepo
            , hpdNotificationDelivery = fakeDelivery
            , hpdGenNotificationId = pure "550e8400-e29b-41d4-a716-446655440000"
            , hpdGenConfirmationCode = pure "123456"
            }
      body =
        "bookingId=BK-A1B2C3&eventType="
          <> TE.encodeUtf8 eventType
          <> "&completionTime=2020-01-01T10%3A00"
          <> "&locationUnlocode=JPTYO&voyageNumber=V001&operatorName=Sato"
      req =
        setPath
          defaultRequest
            { requestMethod = methodPost
            , requestHeaders = [(hContentType, "application/x-www-form-urlencoded")]
            }
          "/handling/new"
  resp <-
    runSession
      ( srequest
          SRequest
            { simpleRequest = req
            , simpleRequestBody = BSL.fromStrict body
            }
      )
      app
  simpleStatus resp `shouldBe` status303
  -- 成功パス (?flash=success) であることを保証 (error 303 との混同防止)
  lookup "Location" (simpleHeaders resp) `shouldBe` Just "/handling/new?flash=success"
  calls <- readIORef callsRef
  pure (calls, ())

spec :: Spec
spec = describe "HandlingPageApi handlerPost UNLOAD 副作用 (T7-C)" $ do
  it "UNLOAD 登録で確認コードが 1 回だけ保存される" $ do
    calls <- postHandling Nothing "UNLOAD"
    calls `shouldBe` ["BK-A1B2C3"]

  it "LOAD 登録では確認コードが保存されない" $ do
    calls <- postHandling Nothing "LOAD"
    calls `shouldBe` []

  it "RECEIVE 登録でも確認コードが保存されない" $ do
    calls <- postHandling Nothing "RECEIVE"
    calls `shouldBe` []

  it "既発行の予約への UNLOAD 再登録は再保存しない (冪等性)" $ do
    calls <- postHandling (Just existingCode) "UNLOAD"
    calls `shouldBe` []

  it "T7-E: UNLOAD 登録で確認コード通知が荷受人向けに保存される" $ do
    notifRef <- newIORef []
    _ <- postHandlingWith (spyNotifRepo notifRef) Nothing "UNLOAD"
    notifs <- readIORef notifRef
    case notifs of
      [n] -> do
        nBookingId n `shouldBe` "BK-A1B2C3"
        ncBody (nContent n) `shouldSatisfy` T.isInfixOf "123456"
      _ -> expectationFailure ("expected 1 notification, got " <> show (length notifs))

  it "T7-E: LOAD 登録では通知が保存されない" $ do
    notifRef <- newIORef []
    _ <- postHandlingWith (spyNotifRepo notifRef) Nothing "LOAD"
    notifs <- readIORef notifRef
    length notifs `shouldBe` 0

  it "T7-E: 既発行 (冪等パス) では通知を再送しない" $ do
    notifRef <- newIORef []
    _ <- postHandlingWith (spyNotifRepo notifRef) (Just existingCode) "UNLOAD"
    notifs <- readIORef notifRef
    length notifs `shouldBe` 0
