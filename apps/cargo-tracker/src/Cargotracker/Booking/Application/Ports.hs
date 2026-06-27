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
import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId)

-- T-01 (IT2): saveBooking は Infrastructure 側の検証失敗 (例: 荷主サロゲート
-- キー解決不可) を例外で潰さず DomainError として返す。Application 層が
-- Either を観測して呼び出し元に伝播できるようにする。
data BookingRepository m = BookingRepository
  { saveBooking :: Cargo -> m (Either DomainError ())
  , findCargoById :: BookingId -> m (Maybe Cargo)
  }

newtype ShipperExistenceChecker m = ShipperExistenceChecker
  { exists :: ShipperId -> m Bool
  }
