{- | Notification BC の出力ポート (US26, IT6)

Notification の永続化 (Repository) と外部配信 (DeliveryPort) を分離する。

- NotificationRepository: DB への保存・更新 (Tx 境界内で完結、T-02 準拠)
- NotificationDeliveryPort: 外部システム連携 (メール送信・ログ配信)
  ADR-0012 決定 3 準拠: Tx 完了後に別途実行する

T-02 準拠: Repository は IO のみ。Tx 境界は Application が管理する。
-}
module Cargotracker.Notification.Application.Ports
  ( NotificationRepository (..),
    NotificationDeliveryPort (..),
    DeliveryResult (..),
  ) where

import Data.Text (Text)

import Cargotracker.Notification.Domain.Model.Notification (Notification)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data NotificationRepository m = NotificationRepository
  { saveNotification :: Notification -> m (Either DomainError ())
  {- ^ 新規通知を Pending 状態で永続化 (通常は INSERT のみ、更新は
  updateNotification で行う)
  -}
  , findByBookingId :: Text -> m [Notification]
  -- ^ 予約 ID で通知履歴を取得 (再送信判定・監査用)
  , updateNotification :: Notification -> m (Either DomainError ())
  -- ^ 状態更新 (Pending → Sent または Failed)
  }

{- | 外部配信の結果。

- DeliverySucceeded: 配信成功、Domain 側で markSent する
- DeliveryFailed reason: 配信失敗、Domain 側で markFailed reason する
-}
data DeliveryResult
  = DeliverySucceeded
  | DeliveryFailed !Text
  deriving stock (Eq, Show)

newtype NotificationDeliveryPort m = NotificationDeliveryPort
  { deliver :: Notification -> m DeliveryResult
  }
