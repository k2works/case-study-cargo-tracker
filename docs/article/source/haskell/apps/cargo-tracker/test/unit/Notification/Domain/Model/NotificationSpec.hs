{-# LANGUAGE OverloadedStrings #-}

{- | Notification 集約と NotificationChannel / NotificationContent の単体テスト
(US26, IT6)

Notification BC は US26「荷受人引取通知」で確立する。荷役 Claim 準備完了
(TsAwaitingClaim 遷移) を購読して、荷受人に確認コード + 引取場所を配信する。

配信手段 (NotificationChannel):
- Log: 構造化ログ (開発 / 監査用)
- EmailMock: メール送信のスタブ (実 SMTP は Notification BC 本格実装で対応)
- PrintableHtml: T5-05 で作成した印刷用 HTML ビューへのリンク

観点:
- Content 生成の受入バリデーション (空 body 拒否)
- 集約 markSent の状態遷移 (Pending → Sent + sentAt 設定)
- markFailed で失敗記録 + 理由保持
-}
module Notification.Domain.Model.NotificationSpec (spec) where

import Data.Maybe (isNothing)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationContent (..),
    NotificationStatus (..),
    markFailed,
    markSent,
    mkNotification,
    mkNotificationContent,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200)

sampleLater :: UTCTime
sampleLater = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43800)

validContent :: NotificationContent
validContent = case mkNotificationContent "引取のご案内" "引取窓口へお越しください。確認コード: 789012" of
  Right c -> c
  Left _ -> error "unreachable"

spec :: Spec
spec = do
  describe "mkNotificationContent (US26)" $ do
    it "空でない subject と body を受理" $
      mkNotificationContent "件名" "本文" `shouldSatisfy` \case
        Right c -> ncSubject c == "件名" && ncBody c == "本文"
        _ -> False

    it "空 subject は InvalidNotificationContent" $
      mkNotificationContent "" "本文"
        `shouldBe` Left (InvalidNotificationContent "subject is empty")

    it "空 body は InvalidNotificationContent" $
      mkNotificationContent "件名" ""
        `shouldBe` Left (InvalidNotificationContent "body is empty")

  describe "mkNotification (US26)" $ do
    it "新規通知は Pending 状態、sentAt = Nothing、failureReason = Nothing" $
      mkNotification "BK-A1B2C3" LogChannel validContent sampleNow
        `shouldSatisfy` \case
          Right n ->
            nBookingId n == "BK-A1B2C3"
              && nChannel n == LogChannel
              && nStatus n == Pending
              && isNothing (nSentAt n)
              && isNothing (nFailureReason n)
          _ -> False

    it "空 bookingId は InvalidBookingId" $
      mkNotification "" LogChannel validContent sampleNow
        `shouldBe` Left (InvalidBookingId "")

  describe "NotificationChannel" $ do
    it "LogChannel / EmailMockChannel / PrintableHtmlChannel の 3 種を扱う" $ do
      let cs = [LogChannel, EmailMockChannel, PrintableHtmlChannel]
      length cs `shouldBe` 3

  describe "markSent (US26 配信成功後の遷移)" $ do
    let notif = case mkNotification "BK-A1B2C3" LogChannel validContent sampleNow of
          Right n -> n
          Left _ -> error "unreachable"

    it "Pending → Sent、sentAt を設定" $ do
      let sent = markSent sampleLater notif
      nStatus sent `shouldBe` Sent
      nSentAt sent `shouldBe` Just sampleLater
      nFailureReason sent `shouldBe` Nothing

    it "既に Sent なら sentAt を上書きしない (idempotent)" $ do
      let sent1 = markSent sampleLater notif
          sent2 = markSent (UTCTime (fromGregorian 2026 12 31) 0) sent1
      nSentAt sent2 `shouldBe` Just sampleLater

  describe "markFailed (US26 配信失敗の記録)" $ do
    let notif = case mkNotification "BK-A1B2C3" EmailMockChannel validContent sampleNow of
          Right n -> n
          Left _ -> error "unreachable"

    it "Pending → Failed、failureReason を保持" $ do
      let failed = markFailed "SMTP timeout" notif
      nStatus failed `shouldBe` Failed
      nFailureReason failed `shouldBe` Just "SMTP timeout"
      nSentAt failed `shouldBe` Nothing

    it "Sent 状態からは Failed に遷移しない (成功済みを覆さない)" $ do
      let sent = markSent sampleLater notif
          maybeFailed = markFailed "later error" sent
      nStatus maybeFailed `shouldBe` Sent
      nFailureReason maybeFailed `shouldBe` Nothing
