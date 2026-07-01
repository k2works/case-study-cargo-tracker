{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の SessionRepository (task 1.2, IT5, ADR-0010)

session テーブル (iter 4 で migration 済) に対する CRUD 操作。
T-02 準拠: Tx 境界は張らない。

users.id (BIGSERIAL) と Domain 層の UserId (Text) の変換:
- session.user_id は BIGINT なので、users テーブルから username を取得して UserId に変換する必要がある
- IT5 段階では簡略化のため username を UserId として扱う (users(id) → users(username) JOIN)
-}
module Cargotracker.Shared.Auth.Infrastructure.PostgresSessionRepository
  ( newPostgresSessionRepository,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Shared.Auth.Application.SessionPorts
  ( SessionRepository (..),
  )
import Cargotracker.Shared.Auth.Domain.Session (Session (..))
import Cargotracker.Shared.Auth.Domain.SessionToken
  ( SessionToken (..),
    unsafeSessionToken,
  )
import Cargotracker.Shared.Auth.Domain.User (UserId (..))
import Cargotracker.Shared.Domain.DomainError (DomainError)

newPostgresSessionRepository :: Connection -> SessionRepository IO
newPostgresSessionRepository conn =
  SessionRepository
    { saveSession = saveImpl conn
    , findByToken = findImpl conn
    , deleteByToken = deleteImpl conn
    , touchLastUsed = touchImpl conn
    }

-- (session_token, username, expires_at, last_used_at)
type SessionRow = (Text, Text, UTCTime, UTCTime)

rowToSession :: SessionRow -> Session
rowToSession (tok, uname, expiry, lastUsed) =
  Session
    { sessionToken = unsafeSessionToken tok
    , sessionUserId = UserId uname
    , sessionExpiresAt = expiry
    , sessionLastUsedAt = lastUsed
    }

saveImpl :: Connection -> Session -> IO (Either DomainError ())
saveImpl conn s = do
  let SessionToken tok = sessionToken s
      UserId uname = sessionUserId s
  _ <-
    execute
      conn
      "INSERT INTO session (session_token, user_id, expires_at, last_used_at) \
      \ SELECT ?, u.id, ?, ? FROM users u WHERE u.username = ? LIMIT 1"
      (tok, sessionExpiresAt s, sessionLastUsedAt s, uname)
  pure (Right ())

findImpl :: Connection -> SessionToken -> IO (Maybe Session)
findImpl conn (SessionToken tok) = do
  rows <-
    query
      conn
      "SELECT s.session_token, u.username, s.expires_at, s.last_used_at \
      \ FROM session s JOIN users u ON u.id = s.user_id \
      \ WHERE s.session_token = ? LIMIT 1"
      (Only tok) ::
      IO [SessionRow]
  pure (rowToSession <$> headMay rows)

deleteImpl :: Connection -> SessionToken -> IO (Either DomainError ())
deleteImpl conn (SessionToken tok) = do
  _ <- execute conn "DELETE FROM session WHERE session_token = ?" (Only tok)
  pure (Right ())

touchImpl :: Connection -> SessionToken -> IO (Either DomainError ())
touchImpl conn (SessionToken tok) = do
  _ <-
    execute
      conn
      "UPDATE session SET last_used_at = NOW() WHERE session_token = ?"
      (Only tok)
  pure (Right ())

headMay :: [a] -> Maybe a
headMay [] = Nothing
headMay (x : _) = Just x
