{-# LANGUAGE OverloadedStrings #-}

{- | NotificationId 値オブジェクト (ADR-0013 Phase 2, IT7)

Notification 集約のサロゲート識別子。UUID v4 (Text 表現) を保持する。
Application 層で採番し (SendClaimNotificationCommand)、
Infrastructure 層で `notification_id` カラムに永続化する
(ADR-0013 Phase 3 で updateNotification WHERE 節を移行予定)。

Cross-BC 連携時は Text-DTO として `notificationIdToText` で export する
(ADR-0004 Rule 4 準拠)。
-}
module Cargotracker.Notification.Domain.Model.Value.NotificationId
  ( NotificationId,
    mkNotificationId,
    notificationIdToText,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

-- | UUID v4 の Text 表現を保持する識別子。
newtype NotificationId = NotificationId {unNotificationId :: Text}
  deriving stock (Eq, Show)

{- | Text から NotificationId を構築する。空文字列は拒否する。
UUID フォーマット検証は Application 層 (UUID v4 生成器) に委譲する
(newtype レベルでは非空チェックのみ)。
-}
mkNotificationId :: Text -> Either DomainError NotificationId
mkNotificationId t
  | T.null t =
      Left (InvalidNotificationContent "notificationId is empty")
  | otherwise = Right (NotificationId t)

notificationIdToText :: NotificationId -> Text
notificationIdToText = unNotificationId
