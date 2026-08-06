{- | セッション集約 (task 1.2, IT5)

session テーブルの 1 レコードを表す Domain 値。ADR-0010 準拠。
Cookie の base64url トークン + user_id + 有効期限 + 最終使用時刻 (sliding window)。
-}
module Cargotracker.Shared.Auth.Domain.Session
  ( Session (..),
    isExpired,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken)
import Cargotracker.Shared.Auth.Domain.User (UserId)

data Session = Session
  { sessionToken :: !SessionToken
  , sessionUserId :: !UserId
  , sessionExpiresAt :: !UTCTime
  , sessionLastUsedAt :: !UTCTime
  }
  deriving stock (Eq, Show)

-- | 現在時刻 now が expires_at を過ぎているかを判定する純粋関数 (T-03)。
isExpired :: UTCTime -> Session -> Bool
isExpired now s = sessionExpiresAt s <= now
