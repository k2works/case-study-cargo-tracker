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
import Data.Maybe (mapMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.Amount (Amount (..))
import Cargotracker.Exception.Domain.Model.DamageException (DamageException (..))
import Cargotracker.Exception.Domain.Model.DelayException (DelayException (..))
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord (..),
    mkExceptionRecord,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    levelToText,
    textToLevel,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType
  ( ExceptionType (..),
    exceptionTypeToText,
  )
import Cargotracker.Exception.Domain.Model.LossException (LossException (..))
import Cargotracker.Exception.Domain.Model.Reporter (Reporter (..), mkReporter)
import Cargotracker.Exception.Infrastructure.DetailJsonParser (parseDetailJson)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresExceptionRepository :: Connection -> ExceptionRepository IO
newPostgresExceptionRepository conn =
  ExceptionRepository
    { saveException = saveImpl conn
    , findExceptionById = findByIdImpl conn
    , findExceptionsByTrackingNumber = findByTnImpl conn
    , updateExceptionResolution = updateImpl conn
    }

-- | exception_record 1 行のカラム順序 (SELECT 節と揃える)
type ExceptionRow =
  ( Text -- exception_id
  , Text -- tracking_number
  , Text -- exception_type
  , Text -- severity
  , Text -- detail_json (::text にキャストして読み出し)
  , Text -- reporter_user_id
  , Text -- reporter_role
  , UTCTime -- reported_at
  , Maybe UTCTime -- resolved_at
  )

findByIdImpl :: Connection -> Text -> IO (Maybe ExceptionRecord)
findByIdImpl conn eid = do
  rows <-
    query
      conn
      "SELECT exception_id, tracking_number, exception_type, severity, \
      \detail_json::text, reporter_user_id, reporter_role, \
      \reported_at, resolved_at \
      \FROM exception_record WHERE exception_id = ? LIMIT 1"
      (Only eid) ::
      IO [ExceptionRow]
  case rows of
    (r : _) -> pure (rowToRecord r)
    [] -> pure Nothing

findByTnImpl :: Connection -> Text -> IO [ExceptionRecord]
findByTnImpl conn tn = do
  rows <-
    query
      conn
      "SELECT exception_id, tracking_number, exception_type, severity, \
      \detail_json::text, reporter_user_id, reporter_role, \
      \reported_at, resolved_at \
      \FROM exception_record WHERE tracking_number = ? \
      \ORDER BY reported_at DESC"
      (Only tn) ::
      IO [ExceptionRow]
  -- 復元失敗行 (壊れた JSON 等) はスキップして無視。ログはこの層では持たない。
  pure (mapMaybe rowToRecord rows)

-- | Row → ExceptionRecord 復元。復元失敗時は Nothing を返す (呼出側でスキップ or 未発見扱い)。
rowToRecord :: ExceptionRow -> Maybe ExceptionRecord
rowToRecord (eid, tn, typ, sevText, detail, uid, role, repAt, resAt) = do
  sev <- textToLevel (T.toUpper sevText)
  case (parseDetailJson typ detail, mkReporter uid role) of
    (Right etype, Right rp) ->
      case mkExceptionRecord eid etype (ExceptionSeverity sev) rp repAt tn of
        Right r -> pure (r {erResolvedAt = resAt})
        Left _ -> Nothing
    _ -> Nothing

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
