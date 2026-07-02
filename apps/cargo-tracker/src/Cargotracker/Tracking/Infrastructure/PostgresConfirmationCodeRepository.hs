{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の ConfirmationCodeRepository (US16, IT5)

confirmation_code テーブルへの保存 (upsert) + 検証結果反映 (attempt_count / used_at)。

T-02 準拠 (Tx 境界は Application 側)。

T5-02 Phase 3b (IT6, SEC-04): 平文 code 列を bcrypt ハッシュ code_hash 列に置換。
- saveConfirmationCode 呼出時に受け取る ConfirmationCode.ccValue (発行直後の平文) を
  hashSecret で bcrypt 化してから INSERT/UPDATE する
- findByBookingId は code_hash を ConfirmationCode.ccValue にロードする
  (呼出側は verifyAndConsumeWith verifySecret で bcrypt 検証する)
-}
module Cargotracker.Tracking.Infrastructure.PostgresConfirmationCodeRepository
  ( newPostgresConfirmationCodeRepository,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Shared.Security.BcryptHash (hashSecret)
import Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.ConfirmationCode
  ( ConfirmationCode (..),
  )

newPostgresConfirmationCodeRepository :: Connection -> ConfirmationCodeRepository IO
newPostgresConfirmationCodeRepository conn =
  ConfirmationCodeRepository
    { saveConfirmationCode = saveImpl conn
    , findByBookingId = findImpl conn
    , updateAfterVerify = updateImpl conn
    }

type ConfirmationRow = (Text, UTCTime, Maybe UTCTime, Int)

rowToCC :: ConfirmationRow -> ConfirmationCode
rowToCC (code, issued, mUsed, attempts) =
  ConfirmationCode
    { ccValue = code
    , ccIssuedAt = issued
    , ccUsedAt = mUsed
    , ccAttemptCount = attempts
    }

saveImpl :: Connection -> Text -> ConfirmationCode -> IO (Either DomainError ())
saveImpl conn bid cc = do
  -- 発行直後の平文 ccValue を bcrypt hash 化してから DB に格納 (SEC-04)
  hashed <- hashSecret (ccValue cc)
  _ <-
    execute
      conn
      "INSERT INTO confirmation_code \
      \ (booking_id, code_hash, issued_at, used_at, attempt_count) \
      \ VALUES (?, ?, ?, ?, ?) \
      \ ON CONFLICT (booking_id) DO UPDATE SET \
      \  code_hash = EXCLUDED.code_hash, \
      \  issued_at = EXCLUDED.issued_at, \
      \  used_at = EXCLUDED.used_at, \
      \  attempt_count = EXCLUDED.attempt_count, \
      \  version = confirmation_code.version + 1, \
      \  updated_at = NOW()"
      ( bid
      , hashed
      , ccIssuedAt cc
      , ccUsedAt cc
      , ccAttemptCount cc
      )
  pure (Right ())

findImpl :: Connection -> Text -> IO (Maybe ConfirmationCode)
findImpl conn bid = do
  rows <-
    query
      conn
      "SELECT code_hash, issued_at, used_at, attempt_count \
      \ FROM confirmation_code WHERE booking_id = ? LIMIT 1"
      (Only bid) ::
      IO [ConfirmationRow]
  pure (rowToCC <$> headMay rows)

updateImpl :: Connection -> Text -> ConfirmationCode -> IO (Either DomainError ())
updateImpl conn bid cc = do
  _ <-
    execute
      conn
      "UPDATE confirmation_code SET \
      \  used_at = ?, \
      \  attempt_count = ?, \
      \  version = version + 1, \
      \  updated_at = NOW() \
      \ WHERE booking_id = ?"
      (ccUsedAt cc, ccAttemptCount cc, bid)
  pure (Right ())

headMay :: [a] -> Maybe a
headMay [] = Nothing
headMay (x : _) = Just x
