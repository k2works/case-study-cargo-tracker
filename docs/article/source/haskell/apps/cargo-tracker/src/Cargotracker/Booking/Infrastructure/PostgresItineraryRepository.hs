{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の ItineraryRepository (US09, IT5 task 2.1)

itinerary + leg テーブルへの保存を実装。iteration_plan-4.md §4.3 の DDL に対応。

T-02 規約: Repository は IO のみで Tx 開始は禁止。Application 層 (ConfirmRouteCommand)
が Tx 境界 API で itinerary + leg + cargo 更新を 1 Tx で括る。

構造:
  itinerary (BIGSERIAL PK, itinerary_id UUID UK, booking_id FK cargo)
  leg (BIGSERIAL PK, itinerary_id UUID FK CASCADE, seq_number, load/unload_location/time, voyage_number)
    UNIQUE (itinerary_id, seq_number) で連番一意性
    CHECK (load_time < unload_time) で区間内時刻順序
-}
module Cargotracker.Booking.Infrastructure.PostgresItineraryRepository
  ( newPostgresItineraryRepository,
  ) where

import Data.List.NonEmpty (NonEmpty)
import qualified Data.List.NonEmpty as NE
import Data.Text (Text)
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    executeMany,
    query,
  )

import Cargotracker.Booking.Application.ItineraryPorts
  ( ItineraryRepository (..),
  )
import Cargotracker.Booking.Domain.Model.Itinerary (Itinerary (..), mkItinerary)
import Cargotracker.Booking.Domain.Model.Leg (Leg (..))
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.ItineraryId
  ( ItineraryId (..),
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresItineraryRepository :: Connection -> ItineraryRepository IO
newPostgresItineraryRepository conn =
  ItineraryRepository
    { saveItinerary = saveItineraryImpl conn
    , findItineraryByBookingId = findByBookingIdImpl conn
    , findItineraryById = findByIdImpl conn
    }

-- | itinerary + leg を挿入する (T-02: Tx 境界は呼び出し元 Application が管理)
saveItineraryImpl ::
  Connection -> BookingId -> Itinerary -> IO (Either DomainError ())
saveItineraryImpl conn (BookingId bid) it = do
  let ItineraryId itid = itItineraryId it
  _ <-
    execute
      conn
      "INSERT INTO itinerary (itinerary_id, booking_id) VALUES (?::uuid, ?)"
      (itid, bid)
  let legRows = fmap (legToRow itid) (NE.toList (itLegs it))
  _ <-
    executeMany
      conn
      "INSERT INTO leg (itinerary_id, seq_number, \
      \ load_location_unlocode, unload_location_unlocode, \
      \ load_time, unload_time, voyage_number) \
      \ VALUES (?::uuid, ?, ?, ?, ?, ?, ?)"
      legRows
  pure (Right ())

legToRow ::
  Text -> Leg -> (Text, Int, Text, Text, UTCTime, UTCTime, Text)
legToRow itid leg =
  ( itid
  , legSeqNumber leg
  , legLoadLocation leg
  , legUnloadLocation leg
  , legLoadTime leg
  , legUnloadTime leg
  , legVoyageNumber leg
  )

findByBookingIdImpl :: Connection -> BookingId -> IO (Maybe Itinerary)
findByBookingIdImpl conn (BookingId bid) = do
  rows <-
    query
      conn
      "SELECT itinerary_id::text FROM itinerary WHERE booking_id = ? LIMIT 1"
      (Only bid) ::
      IO [Only Text]
  case rows of
    (Only itid : _) -> loadItinerary conn itid
    [] -> pure Nothing

findByIdImpl :: Connection -> ItineraryId -> IO (Maybe Itinerary)
findByIdImpl conn (ItineraryId itid) = loadItinerary conn itid

{- | itinerary_id で leg を SELECT し Itinerary を組み立てる。
スマートコンストラクタ (mkItinerary) の接続性検証は再度通す (T-03 純粋関数)。
-}
loadItinerary :: Connection -> Text -> IO (Maybe Itinerary)
loadItinerary conn itid = do
  legRows <-
    query
      conn
      "SELECT seq_number, load_location_unlocode, unload_location_unlocode, \
      \ load_time, unload_time, voyage_number \
      \ FROM leg WHERE itinerary_id = ?::uuid ORDER BY seq_number"
      (Only itid) ::
      IO [(Int, Text, Text, UTCTime, UTCTime, Text)]
  case legRows of
    [] -> pure Nothing
    (r : rs) -> do
      let legs = fmap rowToLeg (r NE.:| rs)
      case mkItinerary (ItineraryId itid) legs of
        Right it -> pure (Just it)
        Left _err -> pure Nothing -- DB データ不整合時は Nothing (WARN ログは呼出元判断)

rowToLeg :: (Int, Text, Text, UTCTime, UTCTime, Text) -> Leg
rowToLeg (seqN, loadLoc, unloadLoc, loadT, unloadT, voy) =
  Leg
    { legSeqNumber = seqN
    , legLoadLocation = loadLoc
    , legUnloadLocation = unloadLoc
    , legLoadTime = loadT
    , legUnloadTime = unloadT
    , legVoyageNumber = voy
    }
