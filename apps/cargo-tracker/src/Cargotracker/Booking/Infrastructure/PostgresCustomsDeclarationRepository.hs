{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE QuasiQuotes #-}

{- | PostgreSQL 実装の CustomsDeclarationRepository (US27, IT3 タスク 5.4)

customs_declaration テーブルへの upsert / find を実装。1 予約 = 0..1 通関情報
のため booking_id に UNIQUE が張られており、`ON CONFLICT (booking_id) DO UPDATE`
で挿入/更新を 1 SQL で表現する。

ADR-0002 T-02: 本モジュールは IO のみで、トランザクション境界 (`withTransaction`)
は Application 層が張る。本実装は単一 SQL なので暫定的にデフォルトトランザクション
に委ねる (Phase 3 で arch-check が gate になる際に明示化する)。
-}
module Cargotracker.Booking.Infrastructure.PostgresCustomsDeclarationRepository
  ( newPostgresCustomsDeclarationRepository,
  ) where

import qualified Data.Text as T
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )
import Database.PostgreSQL.Simple.SqlQQ (sql)

import Cargotracker.Booking.Application.CustomsPorts
  ( CustomsDeclarationRepository (..),
  )
import Cargotracker.Booking.Domain.Model.CustomsDeclaration
  ( CustomsDeclaration (..),
  )
import Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( declarationStatusFromText,
    declarationStatusToText,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.HsCode
  ( mkHsCode,
    unHsCode,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)

newPostgresCustomsDeclarationRepository :: Connection -> CustomsDeclarationRepository IO
newPostgresCustomsDeclarationRepository conn =
  CustomsDeclarationRepository
    { upsertCustomsDeclaration = upsert conn
    , findByBookingId = findOne conn
    }

upsert ::
  Connection ->
  CustomsDeclaration ->
  IO (Either DomainError ())
upsert conn cd = do
  _ <-
    execute
      conn
      [sql|
        INSERT INTO customs_declaration
          (booking_id, hs_code, broker_name, declaration_status, created_at, updated_at)
        VALUES (?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (booking_id) DO UPDATE
          SET hs_code = EXCLUDED.hs_code
            , broker_name = EXCLUDED.broker_name
            , declaration_status = EXCLUDED.declaration_status
            , version = customs_declaration.version + 1
            , updated_at = NOW()
      |]
      ( unBookingId (cdBookingId cd)
      , unHsCode (cdHsCode cd)
      , cdBrokerName cd
      , declarationStatusToText (cdStatus cd)
      )
  pure (Right ())

findOne :: Connection -> BookingId -> IO (Maybe CustomsDeclaration)
findOne conn bid = do
  rows <-
    query
      conn
      [sql|
        SELECT booking_id, hs_code, broker_name, declaration_status
        FROM customs_declaration
        WHERE booking_id = ?
        LIMIT 1
      |]
      (Only (unBookingId bid))
  pure (rowToDeclaration =<< listToMaybe rows)
  where
    listToMaybe [] = Nothing
    listToMaybe (x : _) = Just x

rowToDeclaration ::
  (T.Text, T.Text, T.Text, T.Text) ->
  Maybe CustomsDeclaration
rowToDeclaration (bookingIdText, hsText, broker, statusText) = do
  -- DB CHECK 制約により形式は保証されているが、ドメイン側の不変条件を
  -- 守るために mkHsCode / declarationStatusFromText を経由する。失敗時は
  -- Nothing で表現する (永続化バグの早期検出)。
  hs <- eitherToMaybe (mkHsCode hsText)
  status <- eitherToMaybe (declarationStatusFromText statusText)
  Just
    CustomsDeclaration
      { cdBookingId = BookingId bookingIdText
      , cdHsCode = hs
      , cdBrokerName = broker
      , cdStatus = status
      }
  where
    eitherToMaybe (Right x) = Just x
    eitherToMaybe (Left _) = Nothing
