{- | Booking Application 層のポート (IT1 US04)

- BookingRepository: 自 BC の集約永続化
- ShipperExistenceChecker: 他 BC (Shipper) への参照を ACL 抽象化
-}
module Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  ) where

import Cargotracker.Booking.Domain.Model.Cargo (Cargo)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId)

data BookingRepository m = BookingRepository
  { saveBooking :: Cargo -> m ()
  , findCargoById :: BookingId -> m (Maybe Cargo)
  }

newtype ShipperExistenceChecker m = ShipperExistenceChecker
  { exists :: ShipperId -> m Bool
  }
