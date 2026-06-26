{- | 経路仕様 (IT1 US04)

出発港 / 到着港 / 到着期限の 3 つを束ねる値オブジェクト。
IT1 では構造のみ。同港間予約 (origin == destination) は許容し、
IT2 で経路設計時の `RouteSatisfied` 検証で弾く。
-}
module Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  ) where

import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode)

data RouteSpecification = RouteSpecification
  { origin :: !UnLocode
  , destination :: !UnLocode
  , arrivalDeadline :: !UTCTime
  }
  deriving stock (Eq, Show)
