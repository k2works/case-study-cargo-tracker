{-# LANGUAGE OverloadedStrings #-}

-- | NotificationListView の単体テスト (US26, IT6)
module Notification.Views.NotificationListViewSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Lucid (renderBS)
import Test.Hspec

import Cargotracker.Notification.Views.NotificationListView
  ( NotificationRow (..),
    notificationListPage,
  )

render :: T.Text -> [NotificationRow] -> T.Text
render bid rs = TE.decodeUtf8 (LBC.toStrict (renderBS (notificationListPage bid rs)))

contains :: T.Text -> T.Text -> Bool
contains needle hay = needle `T.isInfixOf` hay

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200)

sentRow :: NotificationRow
sentRow =
  NotificationRow
    { nrCreatedAt = t0
    , nrChannel = "Log"
    , nrStatus = "Sent"
    , nrSentAt = Just t0
    , nrFailureReason = Nothing
    , nrSubject = "引取完了のお知らせ"
    , nrBody = "引取時に確認コード 123456 を提示してください"
    }

failedRow :: NotificationRow
failedRow =
  NotificationRow
    { nrCreatedAt = t0
    , nrChannel = "EmailMock"
    , nrStatus = "Failed"
    , nrSentAt = Nothing
    , nrFailureReason = Just "SMTP timeout"
    , nrSubject = "引取完了のお知らせ"
    , nrBody = "本文"
    }

spec :: Spec
spec = describe "notificationListPage (US26)" $ do
  it "タイトルに通知一覧が含まれる" $
    render "BK-A1B2C3" [] `shouldSatisfy` contains "通知一覧"

  it "予約 ID を表示する" $ do
    let html = render "BK-A1B2C3" []
    html `shouldSatisfy` contains "BK-A1B2C3"
    html `shouldSatisfy` contains "data-testid=\"booking-id\""

  it "履歴なしなら empty-state を表示する" $ do
    let html = render "BK-EMPTY" []
    html `shouldSatisfy` contains "data-testid=\"empty-state\""
    html `shouldSatisfy` contains "まだありません"

  it "履歴ありなら notif-table + notif-row を含む" $ do
    let html = render "BK-A1B2C3" [sentRow]
    html `shouldSatisfy` contains "data-testid=\"notif-table\""
    html `shouldSatisfy` contains "data-testid=\"notif-row\""

  it "Sent 行は bg-success バッジ" $ do
    let html = render "BK-A1B2C3" [sentRow]
    html `shouldSatisfy` contains "badge bg-success"
    html `shouldSatisfy` contains "Log"
    html `shouldSatisfy` contains "引取完了のお知らせ"

  it "Failed 行は bg-danger バッジと失敗理由" $ do
    let html = render "BK-A1B2C3" [failedRow]
    html `shouldSatisfy` contains "badge bg-danger"
    html `shouldSatisfy` contains "SMTP timeout"

  it "複数行が並ぶ" $ do
    let html = render "BK-A1B2C3" [sentRow, failedRow]
    -- 2 行分の data-testid が含まれる (簡易チェック)
    html `shouldSatisfy` \h ->
      T.count "data-testid=\"notif-row\"" h == 2
