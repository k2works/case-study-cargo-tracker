{-# LANGUAGE OverloadedStrings #-}

{- | BillingNotificationAdapter のテスト (US23, IT8)

fake NotificationRepository / DeliveryPort で、精算書発行通知と未払い通知が
Notification BC に正しい件名・本文・通知 ID で保存されることを検証する。
-}
module Billing.Infrastructure.BillingNotificationAdapterSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Billing.Application.Ports (BillingNotificationPort (..))
import Cargotracker.Billing.Infrastructure.BillingNotificationAdapter
  ( newBillingNotificationPort,
  )
import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
    NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationContent (..),
    NotificationStatus (..),
  )

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 10 12) (secondsToDiffTime 43200)

spec :: Spec
spec = describe "BillingNotificationAdapter (US23)" $ do
  it "sendInvoiceNotification で精算書通知が保存・配信される" $ do
    savedRef <- newIORef []
    let repo =
          NotificationRepository
            { saveNotification = \n -> modifyIORef' savedRef (n :) >> pure (Right ())
            , findByBookingId = \_ -> pure []
            , updateNotification = \_ -> pure (Right ())
            }
        delivery = NotificationDeliveryPort {deliver = \_ -> pure DeliverySucceeded}
        port = newBillingNotificationPort repo delivery (pure "uuid-1") (pure sampleNow)
    r <- sendInvoiceNotification port "BK-A1B2C3" "INV-2026-0001"
    r `shouldBe` Right ()
    saved <- readIORef savedRef
    case saved of
      [n] -> do
        nBookingId n `shouldBe` "BK-A1B2C3"
        ncSubject (nContent n) `shouldSatisfy` T.isInfixOf "精算書発行"
        ncSubject (nContent n) `shouldSatisfy` T.isInfixOf "INV-2026-0001"
        nStatus n `shouldBe` Pending
      _ -> expectationFailure ("expected 1 notification, got " <> show (length saved))

  it "sendOverdueNotification で未払い通知が保存される" $ do
    savedRef <- newIORef []
    let repo =
          NotificationRepository
            { saveNotification = \n -> modifyIORef' savedRef (n :) >> pure (Right ())
            , findByBookingId = \_ -> pure []
            , updateNotification = \_ -> pure (Right ())
            }
        delivery = NotificationDeliveryPort {deliver = \_ -> pure DeliverySucceeded}
        port = newBillingNotificationPort repo delivery (pure "uuid-2") (pure sampleNow)
    r <- sendOverdueNotification port "BK-A1B2C3" "INV-2026-0001"
    r `shouldBe` Right ()
    saved <- readIORef savedRef
    map (ncSubject . nContent) saved `shouldSatisfy` all (T.isInfixOf "未払い")
    length saved `shouldBe` 1
