{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の UserRepository (IT1 AUTH 1.3 残り)

postgresql-simple を直接利用する。トランザクション境界 (T-01) は
Application 層の Command から `withTransaction` で張る (IT2 で本格対応)。
本実装は単一の `findByEmail` クエリのみなので IT1 段階では未対応。

スキーマ (db/migrations/20260706120000):
- users (id BIGSERIAL PK, user_id UK, email UK, password_hash, ...)
- user_roles (user_id FK, role, UNIQUE(user_id, role))

ロール選択は IT1 では「最初の 1 件」を使用 (1 ユーザー 1 主ロール想定)。
複数ロール対応は IT2 で claimsRole を [Role] に拡張する。
-}
module Cargotracker.Shared.Auth.Infrastructure.PostgresUserRepository
  ( newPostgresUserRepository,
    findRolesByUserId,
  ) where

import Data.Text (Text)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    query,
  )

import Cargotracker.Shared.Auth.Application.Ports (UserRepository (..))
import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    PasswordHash (..),
    Role (..),
    User (..),
    UserId (..),
  )

newPostgresUserRepository :: Connection -> UserRepository IO
newPostgresUserRepository conn =
  UserRepository
    { findByEmail = \(Email e) -> findUserByEmail conn e
    }

findUserByEmail :: Connection -> Text -> IO (Maybe User)
findUserByEmail conn email_ = do
  rows <-
    query
      conn
      "SELECT u.user_id, u.email, u.password_hash, r.role \
      \ FROM users u \
      \ LEFT JOIN user_roles r ON r.user_id = u.id \
      \ WHERE u.email = ? \
      \ ORDER BY r.id ASC \
      \ LIMIT 1"
      (Only email_) ::
      IO [(Text, Text, Text, Maybe Text)]
  case rows of
    [(uid, em, hash, mRole)] -> case parseRole mRole of
      Just role ->
        pure $
          Just
            User
              { userId = UserId uid
              , userEmail = Email em
              , userPasswordHash = PasswordHash hash
              , userRole = role
              }
      Nothing -> pure Nothing -- ロール未設定ユーザーは認証不可
    _ -> pure Nothing

{- | T7-A (IT8): UserId から全ロールを解決する。RoleGate の
`UserId -> IO [Role]` DI に渡す用途。未知ロール文字列は読み飛ばす。
-}
findRolesByUserId :: Connection -> UserId -> IO [Role]
findRolesByUserId conn (UserId uid) = do
  rows <-
    query
      conn
      "SELECT r.role \
      \ FROM users u \
      \ JOIN user_roles r ON r.user_id = u.id \
      \ WHERE u.user_id = ? \
      \ ORDER BY r.id ASC"
      (Only uid) ::
      IO [Only Text]
  pure [role | Only t <- rows, Just role <- [parseRole (Just t)]]

parseRole :: Maybe Text -> Maybe Role
parseRole Nothing = Nothing
parseRole (Just t) = case t of
  "Shipper" -> Just Shipper
  "Consignee" -> Just Consignee
  "Sales" -> Just Sales
  "Router" -> Just Router
  "Tracker" -> Just Tracker
  "Handler" -> Just Handler
  "Accountant" -> Just Accountant
  "MasterAdmin" -> Just MasterAdmin
  _ -> Nothing
