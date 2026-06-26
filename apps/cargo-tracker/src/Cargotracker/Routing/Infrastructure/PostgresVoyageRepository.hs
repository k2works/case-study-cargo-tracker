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
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
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

newPostgresVoyageRepository :: Connection -> VoyageRepository IO
newPostgresVoyageRepository conn =
  VoyageRepository
    { findByVoyageNumber = findVoyage conn
    , saveVoyage = saveVoy conn
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
