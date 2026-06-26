{- | Shipper Application 層のポート (IT1 US02/US03)

`ShipperRepository` を Application が依存する。Infrastructure 層で
PostgreSQL 実装を注入する (IT1 後半 or IT2 で本実装)。
-}
module Cargotracker.Shipper.Application.Ports
  ( ShipperRepository (..),
  ) where

import Cargotracker.Shipper.Domain.Model.Shipper (Shipper)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail)

data ShipperRepository m = ShipperRepository
  { findByContactEmail :: ContactEmail -> m (Maybe Shipper)
  , save :: Shipper -> m ()
  }
