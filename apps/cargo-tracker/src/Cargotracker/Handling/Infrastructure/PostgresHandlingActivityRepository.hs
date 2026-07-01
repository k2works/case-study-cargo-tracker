{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の HandlingActivityRepository (US15, IT5)

T-02 準拠 (Tx 境界は Application 側)。
-}
module Cargotracker.Handling.Infrastructure.PostgresHandlingActivityRepository
  ( newPostgresHandlingActivityRepository,
  ) where

import Data.Either (rights)
import Data.Text (Text)
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Handling.Application.Ports
  ( HandlingActivityRepository (..),
  )
import Cargotracker.Handling.Domain.Model.HandlingActivity
  ( HandlingActivity (..),
  )
import Cargotracker.Handling.Domain.Model.HandlingType
  ( handlingTypeToText,
    textToHandlingType,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresHandlingActivityRepository :: Connection -> HandlingActivityRepository IO
newPostgresHandlingActivityRepository conn =
  HandlingActivityRepository
    { saveHandlingActivity = saveImpl conn
    , findByBookingId = findByBookingIdImpl conn
    }

type HandlingRow = (Text, Text, UTCTime, Text, Maybe Text, Text)

selectColumns :: Text
selectColumns =
  "booking_id, event_type, event_completion_time, location_unlocode, voyage_number, operator_name"

rowToActivity :: HandlingRow -> Either DomainError HandlingActivity
rowToActivity (bid, evT, ct, loc, mVoy, opName) = do
  ht <- textToHandlingType evT
  Right
    HandlingActivity
      { haBookingId = bid
      , haEventType = ht
      , haCompletionTime = ct
      , haLocationUnlocode = loc
      , haVoyageNumber = mVoy
      , haOperatorName = opName
      }

saveImpl :: Connection -> HandlingActivity -> IO (Either DomainError ())
saveImpl conn a = do
  _ <-
    execute
      conn
      "INSERT INTO handling_activity \
      \ (booking_id, event_type, event_completion_time, \
      \ location_unlocode, voyage_number, operator_name) \
      \ VALUES (?, ?, ?, ?, ?, ?)"
      ( haBookingId a
      , handlingTypeToText (haEventType a)
      , haCompletionTime a
      , haLocationUnlocode a
      , haVoyageNumber a
      , haOperatorName a
      )
  pure (Right ())

findByBookingIdImpl :: Connection -> Text -> IO [HandlingActivity]
findByBookingIdImpl conn bid = do
  rows <-
    query
      conn
      "SELECT booking_id, event_type, event_completion_time, \
      \ location_unlocode, voyage_number, operator_name \
      \ FROM handling_activity WHERE booking_id = ? \
      \ ORDER BY event_completion_time ASC"
      (Only bid) ::
      IO [HandlingRow]
  -- DB CHECK 制約で event_type は必ず有効なので Left は基本発生しない
  pure (rights (map rowToActivity rows))
