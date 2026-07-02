{- | 引取通知送信コマンド (US26, IT6)

Handling BC の T5-04 (Tracking → TsClaimed 遷移) 完了後に呼ばれる想定。
Cross-BC helper `sendClaimNotificationText` を通じて Text ベースで呼ばれる。

ADR-0012 決定 3 準拠のフロー:

1. mkNotificationContent (subject / body 非空検証)
2. mkNotification (Pending 状態で集約構築)
3. saveNotification (Tx 境界内で永続化)
4. Tx 完了後、deliver で外部配信 (Log / Email mock / PrintableHtml)
   - 成功 → markSent + updateNotification (別 Tx)
   - 失敗 → markFailed reason + updateNotification (別 Tx)

呼出側 (Handling BC) は Tx 境界を張らず、Notification Application が
2 段構造で自己完結させる。
-}
module Cargotracker.Notification.Application.SendClaimNotificationCommand
  ( SendClaimNotificationInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
    NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification,
    NotificationChannel,
    markFailed,
    markSent,
    mkNotification,
    mkNotificationContent,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)

data SendClaimNotificationInput = SendClaimNotificationInput
  { inputBookingId :: !Text
  , inputChannel :: !NotificationChannel
  , inputSubject :: !Text
  , inputBody :: !Text
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

{- | 通知を発行し、配信結果を反映する。

戻り値: 発行時点の Notification (deliver 前) と、配信結果 (DeliveryResult) の
      ペアを Right で返す。
      Left は入力検証や永続化失敗を示す。

呼出側は deliver 完了後の Notification (Sent/Failed 状態) を再取得したい場合
findByBookingId を利用する。
-}
execute ::
  Monad m =>
  NotificationRepository m ->
  NotificationDeliveryPort m ->
  SendClaimNotificationInput ->
  m (Either DomainError (Notification, DeliveryResult))
execute repo delivery input =
  case mkNotificationContent (inputSubject input) (inputBody input) of
    Left err -> pure (Left err)
    Right content ->
      case mkNotification
        (inputBookingId input)
        (inputChannel input)
        content
        (inputNow input) of
        Left err -> pure (Left err)
        Right notif -> do
          saveResult <- saveNotification repo notif
          case saveResult of
            Left err -> pure (Left err)
            Right () -> do
              result <- deliver delivery notif
              let updated = case result of
                    DeliverySucceeded -> markSent (inputNow input) notif
                    DeliveryFailed reason -> markFailed reason notif
              _ <- updateNotification repo updated
              pure (Right (updated, result))
