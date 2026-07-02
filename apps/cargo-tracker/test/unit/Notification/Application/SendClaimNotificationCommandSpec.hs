{-# LANGUAGE OverloadedStrings #-}

{- | SendClaimNotificationCommand の単体テスト (US26, IT6)

観点:
- 正常系: mkNotification 成功 → save → deliver 成功 → markSent + update
- 配信失敗系: deliver Failed → markFailed reason + update
- 入力検証: 空 subject / 空 body → InvalidNotificationContent (save は呼ばれない)
- 空 bookingId → InvalidBookingId
- save 失敗: 永続化エラーが伝播 (deliver は呼ばれない)
- 副作用検証: save / update / deliver の呼出回数を IORef で捕捉
-}
module Notification.Application.SendClaimNotificationCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
    NotificationRepository (..),
  )
import Cargotracker.Notification.Application.SendClaimNotificationCommand
  ( SendClaimNotificationInput (..),
    execute,
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationStatus (..),
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200)

sampleInput :: SendClaimNotificationInput
sampleInput =
  SendClaimNotificationInput
    { inputBookingId = "BK-A1B2C3"
    , inputChannel = LogChannel
    , inputSubject = "引取のご案内"
    , inputBody = "引取窓口までお越しください。確認コード: 789012"
    , inputNow = sampleNow
    }

-- IORef spy Repository + Delivery を返すヘルパー
mkSpyReposAndDelivery ::
  DeliveryResult ->
  IO
    ( NotificationRepository IO
    , NotificationDeliveryPort IO
    , IO ([Notification], [Notification], [Notification])
    )
mkSpyReposAndDelivery result = do
  savesRef <- newIORef ([] :: [Notification])
  updatesRef <- newIORef ([] :: [Notification])
  deliversRef <- newIORef ([] :: [Notification])
  let repo =
        NotificationRepository
          { saveNotification = \n -> do
              modifyIORef' savesRef (n :)
              pure (Right ())
          , findByBookingId = \_ -> pure []
          , updateNotification = \n -> do
              modifyIORef' updatesRef (n :)
              pure (Right ())
          }
      delivery =
        NotificationDeliveryPort
          { deliver = \n -> do
              modifyIORef' deliversRef (n :)
              pure result
          }
      readAll = do
        s <- readIORef savesRef
        u <- readIORef updatesRef
        d <- readIORef deliversRef
        pure (s, u, d)
  pure (repo, delivery, readAll)

spec :: Spec
spec = describe "SendClaimNotificationCommand.execute (US26)" $ do
  it "正常系: 配信成功 → markSent + save 1 + deliver 1 + update 1" $ do
    (repo, delivery, readAll) <- mkSpyReposAndDelivery DeliverySucceeded
    result <- execute repo delivery sampleInput
    result `shouldSatisfy` \case
      Right (n, DeliverySucceeded) ->
        nStatus n == Sent && nSentAt n == Just sampleNow
      _ -> False
    (saves, updates, delivers) <- readAll
    length saves `shouldBe` 1
    length delivers `shouldBe` 1
    length updates `shouldBe` 1

  it "配信失敗系: markFailed + failureReason 保持" $ do
    (repo, delivery, readAll) <-
      mkSpyReposAndDelivery (DeliveryFailed "SMTP timeout")
    result <- execute repo delivery sampleInput
    result `shouldSatisfy` \case
      Right (n, DeliveryFailed r) ->
        nStatus n == Failed
          && nFailureReason n == Just "SMTP timeout"
          && r == "SMTP timeout"
      _ -> False
    (_, updates, _) <- readAll
    length updates `shouldBe` 1

  it "空 subject → InvalidNotificationContent、save / deliver は呼ばれない" $ do
    (repo, delivery, readAll) <- mkSpyReposAndDelivery DeliverySucceeded
    result <- execute repo delivery (sampleInput {inputSubject = ""})
    result `shouldBe` Left (InvalidNotificationContent "subject is empty")
    (saves, updates, delivers) <- readAll
    saves `shouldBe` []
    updates `shouldBe` []
    delivers `shouldBe` []

  it "空 body → InvalidNotificationContent" $ do
    (repo, delivery, _) <- mkSpyReposAndDelivery DeliverySucceeded
    result <- execute repo delivery (sampleInput {inputBody = ""})
    result `shouldBe` Left (InvalidNotificationContent "body is empty")

  it "空 bookingId → InvalidBookingId、save は呼ばれない" $ do
    (repo, delivery, readAll) <- mkSpyReposAndDelivery DeliverySucceeded
    result <- execute repo delivery (sampleInput {inputBookingId = ""})
    result `shouldBe` Left (InvalidBookingId "")
    (saves, _, _) <- readAll
    saves `shouldBe` []

  it "save 失敗時: エラー伝播、deliver は呼ばれない" $ do
    deliversRef <- newIORef ([] :: [Notification])
    let repo =
          NotificationRepository
            { saveNotification = \_ -> pure (Left (ConcurrentModification "BK-A1B2C3"))
            , findByBookingId = \_ -> pure []
            , updateNotification = \_ -> pure (Right ())
            }
        delivery =
          NotificationDeliveryPort
            { deliver = \n -> do
                modifyIORef' deliversRef (n :)
                pure DeliverySucceeded
            }
    result <- execute repo delivery sampleInput
    result `shouldBe` Left (ConcurrentModification "BK-A1B2C3")
    delivers <- readIORef deliversRef
    delivers `shouldBe` []

  it "副作用検証: deliver は saveNotification が Right () を返した後にのみ呼ばれる" $ do
    orderRef <- newIORef ([] :: [T.Text])
    let repo =
          NotificationRepository
            { saveNotification = \_ -> do
                modifyIORef' orderRef ("save" :)
                pure (Right ())
            , findByBookingId = \_ -> pure []
            , updateNotification = \_ -> do
                modifyIORef' orderRef ("update" :)
                pure (Right ())
            }
        delivery =
          NotificationDeliveryPort
            { deliver = \_ -> do
                modifyIORef' orderRef ("deliver" :)
                pure DeliverySucceeded
            }
    _ <- execute repo delivery sampleInput
    order <- readIORef orderRef
    reverse order `shouldBe` ["save", "deliver", "update"]
