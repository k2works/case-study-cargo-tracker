{-# LANGUAGE OverloadedStrings #-}

{- | LogDeliveryPort の単体テスト (US26, IT6)

stderr への実書き込みを検証するのは flaky なので、DeliveryPort の
「どの Channel が来ても DeliverySucceeded を返す」不変を確認するだけにする。
実際のログ出力内容は Application 層の SendClaimNotificationCommand テストで
副作用フローとして間接検証される。

観点:
- LogChannel: DeliverySucceeded
- EmailMockChannel: DeliverySucceeded (skip 扱いだが Result は成功)
- PrintableHtmlChannel: DeliverySucceeded
-}
module Notification.Infrastructure.LogDeliveryPortSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( NotificationChannel (..),
    NotificationContent,
    mkNotification,
    mkNotificationContent,
  )
import Cargotracker.Notification.Infrastructure.LogDeliveryPort
  ( newLogDeliveryPort,
  )

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200)

sampleContent :: NotificationContent
sampleContent = case mkNotificationContent "件名" "本文" of
  Right c -> c
  Left _ -> error "unreachable"

runDeliver :: NotificationChannel -> IO DeliveryResult
runDeliver ch =
  case mkNotification "BK-A1B2C3" ch sampleContent sampleNow of
    Right n -> deliver newLogDeliveryPort n
    Left _ -> error "unreachable"

spec :: Spec
spec = describe "LogDeliveryPort (US26)" $ do
  it "LogChannel は DeliverySucceeded を返す (stderr にログ出力あり)" $
    runDeliver LogChannel `shouldReturn` DeliverySucceeded

  it "EmailMockChannel は skip 扱いで DeliverySucceeded を返す" $
    runDeliver EmailMockChannel `shouldReturn` DeliverySucceeded

  it "PrintableHtmlChannel は skip 扱いで DeliverySucceeded を返す" $
    runDeliver PrintableHtmlChannel `shouldReturn` DeliverySucceeded
