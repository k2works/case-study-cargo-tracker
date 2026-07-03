{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の TrackingStateAuditRepository (US17, IT7)

tracking_state_audit テーブルへ手動状態更新の監査ログを追記する。
UPDATE/DELETE は行わない (追記型監査ログ)。

T-02 準拠: Tx 境界は Application 層 (ManualStateUpdateCommand) が管理。
Connection を受け取り INSERT を実行する。
-}
module Cargotracker.Tracking.Infrastructure.PostgresTrackingStateAuditRepository
  ( newPostgresTrackingStateAuditRepository,
  ) where

import Control.Exception (SomeException, try)
import Data.Int (Int64)
import qualified Data.Text as T
import Database.PostgreSQL.Simple
  ( Connection,
    execute,
  )

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (transportStatusToText)
import Cargotracker.Tracking.Application.TrackingStateAuditPorts
  ( TrackingStateAuditRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit
  ( TrackingStateAudit (..),
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (unTrackingNumber)

newPostgresTrackingStateAuditRepository :: Connection -> TrackingStateAuditRepository IO
newPostgresTrackingStateAuditRepository conn =
  TrackingStateAuditRepository
    { saveAudit = saveImpl conn
    }

saveImpl :: Connection -> TrackingStateAudit -> IO (Either DomainError ())
saveImpl conn audit = do
  result <-
    try
      ( execute
          conn
          "INSERT INTO tracking_state_audit \
          \(tracking_number, previous_status, new_status, reason, changed_by, changed_at) \
          \VALUES (?, ?, ?, ?, ?, ?)"
          ( unTrackingNumber (tsaTrackingNumber audit)
          , transportStatusToText (tsaPreviousStatus audit)
          , transportStatusToText (tsaNewStatus audit)
          , tsaReason audit
          , tsaChangedBy audit
          , tsaChangedAt audit
          )
      ) ::
      IO (Either SomeException Int64)
  case result of
    Left e ->
      pure
        ( Left
            ( ConcurrentModification
                ("tracking_state_audit insert failed: " <> T.pack (show e))
            )
        )
    Right _ -> pure (Right ())
