{-# LANGUAGE OverloadedStrings #-}

-- | NotificationListPageApi の hspec-wai 統合テスト (US26, IT6)
module Notification.Interfaces.NotificationListPageApiSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Notification.Application.Ports
  ( NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationStatus (..),
    markSent,
    mkNotification,
    mkNotificationContent,
  )
import Cargotracker.Notification.Interfaces.NotificationListPageApi
  ( notificationListApp,
  )
import Support.HspecWaiJa (bodyContainsText)

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200)

sampleContent :: Notification -> Notification
sampleContent = id -- placeholder if we want to modify later

sentNotification :: Notification
sentNotification =
  case mkNotificationContent "引取完了のお知らせ" "本文" of
    Left _ -> error "content invalid"
    Right c -> case mkNotification "BK-A1B2C3" LogChannel c sampleNow of
      Left _ -> error "notif invalid"
      Right n -> markSent sampleNow n

emptyRepo :: NotificationRepository IO
emptyRepo =
  NotificationRepository
    { saveNotification = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure []
    , updateNotification = \_ -> pure (Right ())
    }

repoWith :: [Notification] -> NotificationRepository IO
repoWith ns =
  NotificationRepository
    { saveNotification = \_ -> pure (Right ())
    , findByBookingId = \bid -> pure (filter ((== bid) . nBookingId) ns)
    , updateNotification = \_ -> pure (Right ())
    }

appEmpty :: IO Network.Wai.Application
appEmpty = pure (notificationListApp emptyRepo)

appWithSent :: IO Network.Wai.Application
appWithSent = pure (notificationListApp (repoWith [sentNotification]))

spec :: Spec
spec = describe "NotificationListPageApi (US26)" $ do
  describe "GET /notifications (bookingId 未指定)" $
    with appEmpty $
      it "bookingId 未指定なら empty-state を含む 200" $
        get "/notifications"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "bookingId が未指定"
            }

  describe "GET /notifications?bookingId=X (履歴なし)" $
    with appEmpty $
      it "履歴なしなら empty-state を表示" $
        get "/notifications?bookingId=BK-EMPTY"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "data-testid=\"empty-state\""
            }

  describe "GET /notifications?bookingId=X (履歴あり)" $
    with appWithSent $ do
      it "notif-table と Sent バッジを表示" $
        get "/notifications?bookingId=BK-A1B2C3"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "data-testid=\"notif-table\""
            }

      it "件名と Log channel が表示される" $
        get "/notifications?bookingId=BK-A1B2C3"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "引取完了のお知らせ"
            }

  describe "GET /notifications?bookingId=X (他予約は表示されない)" $
    with appWithSent $
      it "別予約 ID なら empty-state" $
        get "/notifications?bookingId=BK-OTHER"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "data-testid=\"empty-state\""
            }
