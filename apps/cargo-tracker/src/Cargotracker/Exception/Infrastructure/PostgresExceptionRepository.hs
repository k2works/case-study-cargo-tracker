{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の ExceptionRepository (US19/US20, IT7)

exception_record テーブルへの CRUD 実装。detail_json は Domain の ExceptionType
sum type を素朴な JSON テキストに直接エンコード (Delay/Damage/Loss の主要フィールドを
インラインで保持)。ADR-0014 準拠 (単一テーブル + JSONB 型別詳細)。

T-02 準拠: Tx 境界は Application 層。本モジュールは Connection 受け取り IO のみ。

制限:
- detail_json は最小限の Text エンコード (専用 aeson インスタンスを使わず
  JSON 文字列を組み立てる形)。読み出しはまず生 JSON Text として返し、
  ドメイン再構築 (mkExceptionRecord + 型別詳細) は次反復で対応する
- findExceptionsByTrackingNumber / findExceptionById は暫定的に空を返す
  (詳細 JSON パーサ実装後に本格化、次反復のタスク)
-}
module Cargotracker.Exception.Infrastructure.PostgresExceptionRepository
  ( newPostgresExceptionRepository,
  ) where

import Control.Exception (SomeException, try)
import Data.Int (Int64)
import Data.Text (Text)
import qualified Data.Text as T
import Database.PostgreSQL.Simple
  ( Connection,
    execute,
  )

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.Amount (Amount (..))
import Cargotracker.Exception.Domain.Model.DamageException (DamageException (..))
import Cargotracker.Exception.Domain.Model.DelayException (DelayException (..))
import Cargotracker.Exception.Domain.Model.ExceptionRecord (ExceptionRecord (..))
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    levelToText,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType
  ( ExceptionType (..),
    exceptionTypeToText,
  )
import Cargotracker.Exception.Domain.Model.LossException (LossException (..))
import Cargotracker.Exception.Domain.Model.Reporter (Reporter (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresExceptionRepository :: Connection -> ExceptionRepository IO
newPostgresExceptionRepository conn =
  ExceptionRepository
    { saveException = saveImpl conn
    , findExceptionById = \_ -> pure Nothing
    , findExceptionsByTrackingNumber = \_ -> pure []
    , updateExceptionResolution = updateImpl conn
    }

saveImpl :: Connection -> ExceptionRecord -> IO (Either DomainError ())
saveImpl conn er = do
  result <-
    try
      ( execute
          conn
          "INSERT INTO exception_record \
          \(exception_id, tracking_number, exception_type, severity, detail_json, \
          \ reporter_user_id, reporter_role, reported_at, resolved_at, version) \
          \VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, 0)"
          ( erExceptionId er
          , erTrackingNumber er
          , exceptionTypeToText (erType er)
          , levelToText (unSeverity (erSeverity er))
          , detailJson (erType er)
          , reporterUserId (erReporter er)
          , reporterRole (erReporter er)
          , erReportedAt er
          , erResolvedAt er
          )
      ) ::
      IO (Either SomeException Int64)
  case result of
    Left e ->
      pure
        ( Left
            ( ConcurrentModification
                ("exception_record insert failed: " <> T.pack (show e))
            )
        )
    Right _ -> pure (Right ())

updateImpl :: Connection -> Text -> ExceptionRecord -> IO (Either DomainError ())
updateImpl conn eid updated = do
  result <-
    try
      ( execute
          conn
          "UPDATE exception_record \
          \SET resolved_at = ?, version = version + 1, updated_at = NOW() \
          \WHERE exception_id = ?"
          (erResolvedAt updated, eid)
      ) ::
      IO (Either SomeException Int64)
  case result of
    Left e ->
      pure
        ( Left
            ( ConcurrentModification
                ("exception_record update failed: " <> T.pack (show e))
            )
        )
    Right _ -> pure (Right ())

{- | ExceptionType を最小限の JSON Text にエンコードする。

Delay: {"delayHours":N,"reason":"..."}
Damage: {"amount":N,"currency":"XXX","description":"..."}
Loss:  {"amount":N,"currency":"XXX","lastSeenAt":"..." | null}
-}
detailJson :: ExceptionType -> Text
detailJson (Delay d) =
  "{\"delayHours\":"
    <> T.pack (show (deDelayHours d))
    <> ",\"reason\":"
    <> jsonString (deReason d)
    <> "}"
detailJson (Damage d) =
  "{\"amount\":"
    <> T.pack (show (amValue (daAmount d)))
    <> ",\"currency\":"
    <> jsonString (amCurrency (daAmount d))
    <> ",\"description\":"
    <> jsonString (daDescription d)
    <> "}"
detailJson (Loss l) =
  "{\"amount\":"
    <> T.pack (show (amValue (loAmount l)))
    <> ",\"currency\":"
    <> jsonString (amCurrency (loAmount l))
    <> ",\"lastSeenAt\":"
    <> maybe "null" jsonString (loLastSeenAt l)
    <> "}"

-- | 最小限の JSON 文字列エスケープ (\" と \\)。
jsonString :: Text -> Text
jsonString t = "\"" <> T.concatMap escape t <> "\""
  where
    escape '"' = "\\\""
    escape '\\' = "\\\\"
    escape c = T.singleton c
