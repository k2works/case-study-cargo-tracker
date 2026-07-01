{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の TrackingRepository (US14, IT5 Step 2)

tracking_activity テーブルへの保存を実装。既存 PostgresBookingRepository の
パターンを踏襲し、行タプル → Domain 集約への reconstruct 関数を明示する。

T-02 規約: Tx 境界は張らない (Application が管理)。
-}
module Cargotracker.Tracking.Infrastructure.PostgresTrackingRepository
  ( newPostgresTrackingRepository,
  ) where

import Data.Text (Text)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Shared.Domain.TransportStatus
  ( textToTransportStatus,
    transportStatusToText,
  )
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber (..),
    unsafeTrackingNumber,
  )

newPostgresTrackingRepository :: Connection -> TrackingRepository IO
newPostgresTrackingRepository conn =
  TrackingRepository
    { saveTracking = saveImpl conn
    , findByBookingId = findByBookingIdImpl conn
    , findByTrackingNumber = findByTrackingNumberImpl conn
    }

-- | tracking_activity テーブル 1 行の Text 表現 (SELECT 順に対応)。
type TrackingRow = (Text, Text, Text, Int)

trackingSelectColumns :: Text
trackingSelectColumns = "tracking_number, booking_id, transport_status, version"

rowToActivity :: TrackingRow -> TrackingActivity
rowToActivity (tnT, bidT, tsT, ver) =
  TrackingActivity
    { taTrackingNumber = unsafeTrackingNumber tnT
    , taBookingId = bidT
    , taTransportStatus = textToTransportStatus tsT
    , taVersion = ver
    }

saveImpl :: Connection -> TrackingActivity -> IO (Either DomainError ())
saveImpl conn a = do
  let TrackingNumber tn = taTrackingNumber a
      tsT = transportStatusToText (taTransportStatus a)
  _ <-
    execute
      conn
      "INSERT INTO tracking_activity \
      \ (tracking_number, booking_id, transport_status, version) \
      \ VALUES (?, ?, ?, ?)"
      (tn, taBookingId a, tsT, taVersion a)
  pure (Right ())

findByBookingIdImpl :: Connection -> Text -> IO (Maybe TrackingActivity)
findByBookingIdImpl conn bid = do
  rows <-
    query
      conn
      "SELECT tracking_number, booking_id, transport_status, version \
      \ FROM tracking_activity WHERE booking_id = ? LIMIT 1"
      (Only bid) ::
      IO [TrackingRow]
  pure (rowToActivity <$> headMay rows)

findByTrackingNumberImpl ::
  Connection -> TrackingNumber -> IO (Maybe TrackingActivity)
findByTrackingNumberImpl conn (TrackingNumber tn) = do
  rows <-
    query
      conn
      "SELECT tracking_number, booking_id, transport_status, version \
      \ FROM tracking_activity WHERE tracking_number = ? LIMIT 1"
      (Only tn) ::
      IO [TrackingRow]
  pure (rowToActivity <$> headMay rows)

headMay :: [a] -> Maybe a
headMay [] = Nothing
headMay (x : _) = Just x
