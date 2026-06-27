{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の EstimateRepository (US01, IT2)

集約 (estimate) と配下エンティティ (route_candidate) を 1 トランザクション
で永続化する。Shipper の業務キー (SHP-XXXXXX) はサロゲートキー (shipper.id)
に解決してから INSERT する (PostgresBookingRepository と同じパターン)。

ACL 規約 (T-06 Rule 4):
- Shipper / Routing BC の値オブジェクトに直接依存しない。
- estimate.shipper_id は INSERT 時にサロゲートキーへ変換するが、
  SELECT 時には estimate に保持された Text (`shipperIdText`) は
  「業務キー文字列」として復元する。
-}
module Cargotracker.Estimation.Infrastructure.PostgresEstimateRepository
  ( newPostgresEstimateRepository,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
    withTransaction,
  )

import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate (..),
  )
import Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate (..),
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateId
  ( EstimateId (..),
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateStatus
  ( EstimateStatus (..),
    estimateStatusToText,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresEstimateRepository :: Connection -> EstimateRepository IO
newPostgresEstimateRepository conn =
  EstimateRepository
    { saveEstimate = saveEst conn
    , findEstimateById = findEst conn
    }

saveEst :: Connection -> Estimate -> IO (Either DomainError ())
saveEst conn e = withTransaction conn $ do
  shipperRows <-
    query
      conn
      "SELECT id FROM shipper WHERE shipper_id = ? LIMIT 1"
      (Only (shipperIdText e)) ::
      IO [Only Int]
  case shipperRows of
    [Only shipperPk] -> do
      let EstimateId eidT = estimateId e
          UnLocode origT = origin e
          UnLocode destT = destination e
      estRows <-
        query
          conn
          "INSERT INTO estimate \
          \  (estimate_id, shipper_id, origin_unlocode, destination_unlocode, \
          \   deadline, cargo_type, weight_kg, estimate_status, version) \
          \ VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, 1) RETURNING id"
          ( eidT
          , shipperPk
          , origT
          , destT
          , deadline e
          , cargoTypeText e
          , weightKg e
          , estimateStatusToText (estimateStatus e)
          ) ::
          IO [Only Int]
      case estRows of
        [Only estDbId] -> do
          mapM_ (insertCandidate conn estDbId) (routeCandidates e)
          pure (Right ())
        _ ->
          pure
            ( Left
                (ShipperNotFound ("estimate insert returned no id: " <> eidT))
            )
    _ ->
      pure (Left (ShipperNotFound (shipperIdText e)))

insertCandidate :: Connection -> Int -> RouteCandidate -> IO ()
insertCandidate conn estDbId rc = do
  _ <-
    execute
      conn
      "INSERT INTO route_candidate \
      \  (estimate_id, rank, transit_days, estimated_cost, voyage_numbers) \
      \ VALUES (?, ?, ?, ?, ?)"
      ( estDbId
      , rank rc
      , transitDays rc
      , estimatedCost rc
      , T.intercalate "," (voyageNumbers rc)
      )
  pure ()

findEst :: Connection -> EstimateId -> IO (Maybe Estimate)
findEst conn (EstimateId eidT) = withTransaction conn $ do
  estRows <-
    query
      conn
      "SELECT e.id, e.estimate_id, s.shipper_id, e.origin_unlocode, e.destination_unlocode, \
      \        e.deadline, e.cargo_type, e.weight_kg, e.estimate_status \
      \ FROM estimate e JOIN shipper s ON s.id = e.shipper_id \
      \ WHERE e.estimate_id = ?::uuid LIMIT 1"
      (Only eidT) ::
      IO
        [(Int, Text, Text, Text, Text, UTCTime, Text, Double, Text)]
  case estRows of
    [(estDbId, eidV, sidT, orig, dest, dl, ctype, wt, statusT)] -> do
      candidates <- loadCandidates conn estDbId
      let status = case statusT of
            "Expired" -> Expired
            _ -> Created
      pure $
        Just
          Estimate
            { estimateId = EstimateId eidV
            , shipperIdText = sidT
            , origin = UnLocode orig
            , destination = UnLocode dest
            , deadline = dl
            , cargoTypeText = ctype
            , weightKg = wt
            , estimateStatus = status
            , routeCandidates = candidates
            }
    _ -> pure Nothing

loadCandidates :: Connection -> Int -> IO [RouteCandidate]
loadCandidates conn estDbId = do
  rows <-
    query
      conn
      "SELECT rank, transit_days, estimated_cost, voyage_numbers \
      \ FROM route_candidate WHERE estimate_id = ? ORDER BY rank ASC"
      (Only estDbId) ::
      IO [(Int, Int, Int, Text)]
  pure
    [ RouteCandidate
        { rank = r
        , transitDays = td
        , estimatedCost = c
        , voyageNumbers = T.splitOn "," vns
        }
    | (r, td, c, vns) <- rows
    ]
