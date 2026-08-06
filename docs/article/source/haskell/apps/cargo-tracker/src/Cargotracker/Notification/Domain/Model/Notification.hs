{- | Notification 集約 (US26, IT6, Notification BC 新設)

引取通知の Domain 型。

配信手段 (NotificationChannel):
- LogChannel: 構造化ログ (開発 / 監査用、常時利用)
- EmailMockChannel: メール送信スタブ (実 SMTP は本番配信で対応)
- PrintableHtmlChannel: T5-05 で作成した印刷用 HTML ビュー

状態 (NotificationStatus):
- Pending: 新規発行、未配信
- Sent: 配信成功 (sentAt に時刻記録)
- Failed: 配信失敗 (failureReason に理由保持)

T-03 準拠: 全関数純粋。実送信は Application/Infrastructure 層で行い、
Domain は状態遷移と VO の検証に閉じる。
-}
module Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationContent (..),
    NotificationStatus (..),
    mkNotificationContent,
    mkNotification,
    mkNotificationWithId,
    markSent,
    markFailed,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Notification.Domain.Model.Value.NotificationId (NotificationId)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

-- | 通知本文。件名と本文の非空性のみ検証する。
data NotificationContent = NotificationContent
  { ncSubject :: !Text
  , ncBody :: !Text
  }
  deriving stock (Eq, Show)

mkNotificationContent :: Text -> Text -> Either DomainError NotificationContent
mkNotificationContent subj body
  | T.null subj = Left (InvalidNotificationContent "subject is empty")
  | T.null body = Left (InvalidNotificationContent "body is empty")
  | otherwise = Right (NotificationContent subj body)

-- | 配信手段。IT6 の暫定範囲では 3 種を扱う。
data NotificationChannel
  = LogChannel
  | EmailMockChannel
  | PrintableHtmlChannel
  deriving stock (Eq, Show)

-- | 配信ステータス。
data NotificationStatus
  = Pending
  | Sent
  | Failed
  deriving stock (Eq, Show)

{- | 通知集約。1 荷受人あたり複数の Notification が発行され得る。
ADR-0013 Phase 2 (IT7): 業務キー変更耐性のためサロゲート識別子
`nId :: Maybe NotificationId` を追加する (Nothing は既存レコードとの
互換性維持のための一時状態)。Phase 3 完了時に Maybe を外す予定。
-}
data Notification = Notification
  { nId :: !(Maybe NotificationId)
  -- ^ ADR-0013 サロゲート識別子 (UUID v4)。Phase 3 で非 Maybe 化予定。
  , nBookingId :: !Text
  , nChannel :: !NotificationChannel
  , nContent :: !NotificationContent
  , nStatus :: !NotificationStatus
  , nCreatedAt :: !UTCTime
  , nSentAt :: !(Maybe UTCTime)
  , nFailureReason :: !(Maybe Text)
  }
  deriving stock (Eq, Show)

{- | 新規通知を Pending 状態で生成する。

ADR-0013 移行期 (IT7 Phase 2): nId は Nothing で初期化する。
Phase 3 で `mkNotificationWithId` を経由し UUID v4 を注入するよう
Application 層 (SendClaimNotificationCommand) を書き換える。
-}
mkNotification ::
  Text ->
  NotificationChannel ->
  NotificationContent ->
  UTCTime ->
  Either DomainError Notification
mkNotification bid ch content now
  | T.null bid = Left (InvalidBookingId bid)
  | otherwise =
      Right
        Notification
          { nId = Nothing
          , nBookingId = bid
          , nChannel = ch
          , nContent = content
          , nStatus = Pending
          , nCreatedAt = now
          , nSentAt = Nothing
          , nFailureReason = Nothing
          }

{- | ADR-0013 Phase 2: サロゲート識別子を明示的に指定して新規通知を生成する。
`SendClaimNotificationCommand` から UUID v4 を採番して呼び出す想定。
-}
mkNotificationWithId ::
  NotificationId ->
  Text ->
  NotificationChannel ->
  NotificationContent ->
  UTCTime ->
  Either DomainError Notification
mkNotificationWithId nid bid ch content now =
  fmap (\n -> n {nId = Just nid}) (mkNotification bid ch content now)

{- | 配信成功として Sent に遷移し、sentAt を設定する。

既に Sent 状態なら sentAt を上書きしない (idempotent、
外部システム再送による多重配信対策)。
-}
markSent :: UTCTime -> Notification -> Notification
markSent now n = case nStatus n of
  Sent -> n
  _ -> n {nStatus = Sent, nSentAt = Just now, nFailureReason = Nothing}

{- | 配信失敗として Failed に遷移し、理由を保持する。

Sent 状態からは Failed に遷移しない (成功済みを覆さない)。
Pending / Failed からは新しい理由で更新される。
-}
markFailed :: Text -> Notification -> Notification
markFailed reason n = case nStatus n of
  Sent -> n
  _ -> n {nStatus = Failed, nFailureReason = Just reason}
