{- | Shipper Application 層のポート (IT1 US02/US03)

`ShipperRepository` を Application が依存する。Infrastructure 層で
PostgreSQL 実装を注入する (IT1 後半 or IT2 で本実装)。
-}
module Cargotracker.Shipper.Application.Ports
  ( ShipperRepository (..),
  ) where

import Cargotracker.Shipper.Domain.Model.Shipper (Shipper)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId)

data ShipperRepository m = ShipperRepository
  { findByContactEmail :: ContactEmail -> m (Maybe Shipper)
  , findById :: ShipperId -> m (Maybe Shipper)
  , save :: Shipper -> m ()
  , searchByQuery :: ContactEmail -> m [Shipper]
  , findAllShippers :: m [Shipper]
  -- ^ IT2 一覧画面用 (暫定ページング無し、最大 100 件) — IT4 で findShippersPaged へ移行 (ADR-0006)
  }

{-# DEPRECATED
  findAllShippers
  "ADR-0006 PG-03: IT4 で findShippersPaged :: PageReq -> m (Page Shipper) へ移行する。\
  \新規 callsite の追加は避け、既存 callsite は段階的に移行すること。"
  #-}
