{-# LANGUAGE OverloadedStrings #-}

{- | Notification BC の PostgreSQL 実装 NotificationRepository (US26 Phase 7, IT6)

notification テーブルへの save / find / update を実装。

T-02 準拠 (Tx 境界は Application 層で管理)。ステータス変更 (Pending → Sent /
Failed) は updateNotification 経由で単一レコードを ID (booking_id + created_at)
で置換する。
-}
module Cargotracker.Notification.Infrastructure.PostgresNotificationRepository
  ( newPostgresNotificationRepository,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Notification.Application.Ports
  ( NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationContent (..),
    NotificationStatus (..),
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)

newPostgresNotificationRepository :: Connection -> NotificationRepository IO
newPostgresNotificationRepository conn =
  NotificationRepository
    { saveNotification = saveImpl conn
    , findByBookingId = findByBookingIdImpl conn
    , updateNotification = updateImpl conn
    }

--------------------------------------------------------------------------------
-- 変換ヘルパ
--------------------------------------------------------------------------------

channelToText :: NotificationChannel -> Text
channelToText LogChannel = "Log"
channelToText EmailMockChannel = "EmailMock"
channelToText PrintableHtmlChannel = "PrintableHtml"

textToChannel :: Text -> NotificationChannel
textToChannel "EmailMock" = EmailMockChannel
textToChannel "PrintableHtml" = PrintableHtmlChannel
textToChannel _ = LogChannel

statusToText :: NotificationStatus -> Text
statusToText Pending = "Pending"
statusToText Sent = "Sent"
statusToText Failed = "Failed"

textToStatus :: Text -> NotificationStatus
textToStatus "Sent" = Sent
textToStatus "Failed" = Failed
textToStatus _ = Pending

type NotificationRow =
  (Text, Text, Text, Text, Text, UTCTime, Maybe UTCTime, Maybe Text)

rowToNotification :: NotificationRow -> Notification
rowToNotification (bid, chan, subj, body, status, created, sent, failure) =
  Notification
    { nBookingId = bid
    , nChannel = textToChannel chan
    , nContent = NotificationContent subj body
    , nStatus = textToStatus status
    , nCreatedAt = created
    , nSentAt = sent
    , nFailureReason = failure
    }

--------------------------------------------------------------------------------
-- 実装
--------------------------------------------------------------------------------

saveImpl :: Connection -> Notification -> IO (Either DomainError ())
saveImpl conn n = do
  _ <-
    execute
      conn
      "INSERT INTO notification \
      \ (booking_id, channel, subject, body, status, created_at, sent_at, failure_reason) \
      \ VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      ( nBookingId n
      , channelToText (nChannel n)
      , ncSubject (nContent n)
      , ncBody (nContent n)
      , statusToText (nStatus n)
      , nCreatedAt n
      , nSentAt n
      , nFailureReason n
      )
  pure (Right ())

findByBookingIdImpl :: Connection -> Text -> IO [Notification]
findByBookingIdImpl conn bid = do
  rows <-
    query
      conn
      "SELECT booking_id, channel, subject, body, status, created_at, sent_at, failure_reason \
      \ FROM notification \
      \ WHERE booking_id = ? \
      \ ORDER BY created_at DESC"
      (Only bid) ::
      IO [NotificationRow]
  pure (fmap rowToNotification rows)

updateImpl :: Connection -> Notification -> IO (Either DomainError ())
updateImpl conn n = do
  _ <-
    execute
      conn
      "UPDATE notification SET \
      \  status = ?, sent_at = ?, failure_reason = ?, \
      \  version = version + 1, updated_at = NOW() \
      \ WHERE booking_id = ? AND created_at = ?"
      ( statusToText (nStatus n)
      , nSentAt n
      , nFailureReason n
      , nBookingId n
      , nCreatedAt n
      )
  pure (Right ())

-- silence unused import (T.pack 用途など将来拡張)
_unused :: Text -> Text
_unused = T.strip
