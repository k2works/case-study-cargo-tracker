{-# LANGUAGE OverloadedStrings #-}

{- | ShipperExistenceChecker の PostgreSQL 実装 (IT1 US04)

Booking BC が Shipper BC を ACL 経由で参照する際のポート実装。
business key (shipper_id) で存在確認する。
-}
module Cargotracker.Booking.Infrastructure.PostgresShipperExistenceChecker
  ( newPostgresShipperExistenceChecker,
  ) where

import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    query,
  )

import Cargotracker.Booking.Application.Ports (ShipperExistenceChecker (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

newPostgresShipperExistenceChecker :: Connection -> ShipperExistenceChecker IO
newPostgresShipperExistenceChecker conn =
  ShipperExistenceChecker
    { exists = \(ShipperRef sid) -> do
        rows <-
          query
            conn
            "SELECT 1 FROM shipper WHERE shipper_id = ? LIMIT 1"
            (Only sid) ::
            IO [Only Int]
        pure (not (null rows))
    }
