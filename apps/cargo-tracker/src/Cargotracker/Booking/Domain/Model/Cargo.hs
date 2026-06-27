{- | Cargo 集約ルート (IT1 US04)

予約の中心エンティティ。荷主 (`ShipperId`) を ACL 参照として保持し、
状態遷移は `BookingStatus` 経由で管理する。

楽観ロックは `cargoVersion` で表現 (新規 = 1、各更新で +1)。
-}
module Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
    submitBooking,
    requestRouting,
  ) where

import qualified Data.Text as T

import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId, unShipperId)

data Cargo = Cargo
  { cargoBookingId :: !BookingId
  , cargoShipperId :: !ShipperId
  , cargoRouteSpec :: !RouteSpecification
  , cargoStatus :: !BookingStatus
  , cargoVersion :: !Int
  }
  deriving stock (Eq, Show)

mkCargo :: BookingId -> ShipperId -> RouteSpecification -> Cargo
mkCargo bid sid route =
  Cargo
    { cargoBookingId = bid
    , cargoShipperId = sid
    , cargoRouteSpec = route
    , cargoStatus = Draft
    , cargoVersion = 1
    }

{- | 予約を確定送信する (Draft → Submitted)。
すでに Submitted 以降の状態にあるとエラー。
-}
submitBooking :: Cargo -> Either DomainError Cargo
submitBooking cargo = case cargoStatus cargo of
  Draft ->
    Right
      cargo
        { cargoStatus = Submitted
        , cargoVersion = cargoVersion cargo + 1
        }
  _ -> Left (ConcurrentModification (unShipperId (cargoShipperId cargo)))

{- | 予約を経路設計者に引き渡す (Submitted → RouteProposed) (US06, IT2)。

Submitted 以外の状態からは InvalidStateTransition を返し、
二重引き渡し・順序違反 (Draft からの直接引き渡し等) を防ぐ。
-}
requestRouting :: Cargo -> Either DomainError Cargo
requestRouting cargo = case cargoStatus cargo of
  Submitted ->
    Right
      cargo
        { cargoStatus = RouteProposed
        , cargoVersion = cargoVersion cargo + 1
        }
  other ->
    Left
      ( InvalidStateTransition
          (T.pack (show other))
          (T.pack (show RouteProposed))
      )
