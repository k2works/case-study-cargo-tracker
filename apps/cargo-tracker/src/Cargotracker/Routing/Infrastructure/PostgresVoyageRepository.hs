{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の VoyageRepository (IT1 US24 4.4)

voyage + carrier_movement の 1:N 構造を扱う。集約境界 (voyage が
carrier_movement を所有) を尊重し、save では withTransaction で
両テーブルへの挿入をアトミックに行う。

スキーマ (db/migrations/20260706120400):
- voyage (id BIGSERIAL PK, voyage_number UK, version, ...)
- carrier_movement (id BIGSERIAL PK, voyage_id BIGINT FK, seq_number, ...)
  UNIQUE (voyage_id, seq_number)
-}
module Cargotracker.Routing.Infrastructure.PostgresVoyageRepository
  ( newPostgresVoyageRepository,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
    query_,
    withTransaction,
  )

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber (..),
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresVoyageRepository :: Connection -> VoyageRepository IO
newPostgresVoyageRepository conn =
  VoyageRepository
    { findByVoyageNumber = findVoyage conn
    , saveVoyage = saveVoy conn
    , updateVoyage = updateVoy conn
    , findAllVoyages = listVoyages conn
    }

listVoyages :: Connection -> IO [Voyage]
listVoyages conn = do
  rows <-
    query_
      conn
      "SELECT voyage_number, version FROM voyage ORDER BY voyage_number LIMIT 100" ::
      IO [(Text, Int)]
  mapM (loadOne conn) rows
  where
    loadOne :: Connection -> (Text, Int) -> IO Voyage
    loadOne c (vn, ver) = do
      mvs <-
        query
          c
          "SELECT v.id, departure_location_unlocode, arrival_location_unlocode, \
          \        departure_time, arrival_time \
          \ FROM carrier_movement cm \
          \ JOIN voyage v ON v.id = cm.voyage_id \
          \ WHERE v.voyage_number = ? ORDER BY seq_number ASC"
          (Only vn) ::
          IO [(Int, Text, Text, UTCTime, UTCTime)]
      pure
        Voyage
          { voyageNumber = VoyageNumber vn
          , voyageVersion = ver
          , carrierMovements =
              [ CarrierMovement
                  { departureLocation = UnLocode dep
                  , arrivalLocation = UnLocode arr
                  , departureTime = depT
                  , arrivalTime = arrT
                  }
              | (_, dep, arr, depT, arrT) <- mvs
              ]
          }

findVoyage :: Connection -> VoyageNumber -> IO (Maybe Voyage)
findVoyage conn (VoyageNumber vn) = do
  voyageRows <-
    query
      conn
      "SELECT id, voyage_number, version FROM voyage WHERE voyage_number = ? LIMIT 1"
      (Only vn) ::
      IO [(Int, Text, Int)]
  case voyageRows of
    [(voyId, voyNumText, ver)] -> do
      movements <-
        query
          conn
          "SELECT departure_location_unlocode, arrival_location_unlocode, \
          \        departure_time, arrival_time \
          \   FROM carrier_movement \
          \  WHERE voyage_id = ? \
          \  ORDER BY seq_number ASC"
          (Only voyId)
      pure $
        Just
          Voyage
            { voyageNumber = VoyageNumber voyNumText
            , carrierMovements = map toMovement movements
            , voyageVersion = ver
            }
    _ -> pure Nothing
  where
    toMovement (dep, arr, depTime, arrTime) =
      CarrierMovement
        { departureLocation = UnLocode dep
        , arrivalLocation = UnLocode arr
        , departureTime = depTime
        , arrivalTime = arrTime
        }

saveVoy :: Connection -> Voyage -> IO ()
saveVoy conn v = withTransaction conn $ do
  let VoyageNumber vn = voyageNumber v
  rows <-
    query
      conn
      "INSERT INTO voyage (voyage_number, version) VALUES (?, ?) RETURNING id"
      (vn, voyageVersion v) ::
      IO [Only Int]
  case rows of
    [Only voyId] -> mapM_ (insertMovement conn voyId) (zip [1 ..] (carrierMovements v))
    _ -> error "PostgresVoyageRepository: voyage insert returned no id"

-- US25 (IT2): 既存 voyage の version を楽観ロックし、carrier_movement を
-- 全削除→再 INSERT で差し替える。3 ステートメントを `withTransaction` で
-- ラップし ADR 0002 T-01 (Application/Infrastructure 境界) を満たす。
updateVoy :: Connection -> Voyage -> IO (Either DomainError ())
updateVoy conn v = withTransaction conn $ do
  let VoyageNumber vn = voyageNumber v
      newVersion = voyageVersion v
      expectedVersion = newVersion - 1
  rows <-
    query
      conn
      "SELECT id FROM voyage WHERE voyage_number = ? AND version = ? \
      \ FOR UPDATE"
      (vn, expectedVersion) ::
      IO [Only Int]
  case rows of
    [Only voyId] -> do
      _ <-
        execute
          conn
          "DELETE FROM carrier_movement WHERE voyage_id = ?"
          (Only voyId)
      mapM_ (insertMovement conn voyId) (zip [1 ..] (carrierMovements v))
      _ <-
        execute
          conn
          "UPDATE voyage SET version = ? WHERE id = ?"
          (newVersion, voyId)
      pure (Right ())
    _ -> pure (Left (ConcurrentModification vn))

insertMovement :: Connection -> Int -> (Int, CarrierMovement) -> IO ()
insertMovement conn voyId (seqNo, m) = do
  let UnLocode dep = departureLocation m
      UnLocode arr = arrivalLocation m
  _ <-
    execute
      conn
      "INSERT INTO carrier_movement \
      \ (voyage_id, seq_number, departure_location_unlocode, arrival_location_unlocode, \
      \  departure_time, arrival_time) \
      \ VALUES (?, ?, ?, ?, ?, ?)"
      ( voyId
      , seqNo
      , dep
      , arr
      , departureTime m
      , arrivalTime m
      )
  pure ()
