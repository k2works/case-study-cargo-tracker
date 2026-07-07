{-# LANGUAGE OverloadedStrings #-}

{- | BillingNotificationPort の Notification BC アダプタ (US23, IT8)

精算書発行通知 (荷主宛) と未払い通知 (経理担当者宛) を、Notification BC の
Cross-BC helper `sendClaimLogNotificationTextWithId` (Text ベース、Rule 4
準拠) に委譲する。Handling BC → Notification BC と同じ依存方向・パターン。

ADR-0013 Phase 3: 通知 ID は呼出側採番の UUID v4 (genNid) を注入する。
-}
module Cargotracker.Billing.Infrastructure.BillingNotificationAdapter
  ( newBillingNotificationPort,
  ) where

import Control.Monad (void)
import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Billing.Application.Ports (BillingNotificationPort (..))
import Cargotracker.Notification.Application.Ports
  ( NotificationDeliveryPort,
    NotificationRepository,
  )
import Cargotracker.Notification.Application.SendClaimNotificationCommand
  ( sendClaimLogNotificationTextWithId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)

newBillingNotificationPort ::
  NotificationRepository IO ->
  NotificationDeliveryPort IO ->
  -- | Notification UUID v4 生成器 (ADR-0013 Phase 3)
  IO Text ->
  -- | 現在時刻
  IO UTCTime ->
  BillingNotificationPort IO
newBillingNotificationPort repo delivery genNid nowM =
  BillingNotificationPort
    { sendInvoiceNotification = \bid inum ->
        send
          bid
          ("精算書発行のお知らせ (" <> inum <> ")")
          ( "予約 "
              <> bid
              <> " の精算書 "
              <> inum
              <> " を発行しました。"
              <> "支払期日までに入金をお願いします。"
          )
    , sendOverdueNotification = \bid inum ->
        send
          bid
          ("未払い通知 (" <> inum <> ")")
          ( "予約 "
              <> bid
              <> " の精算書 "
              <> inum
              <> " が支払期限を超過しました。"
              <> "経理担当者は入金状況を確認してください。"
          )
    }
  where
    send :: Text -> Text -> Text -> IO (Either DomainError ())
    send bid subj body = do
      now <- nowM
      nid <- genNid
      result <-
        sendClaimLogNotificationTextWithId repo delivery bid subj body now (Just nid)
      pure (void result)
