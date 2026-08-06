{- | 輸送区間 (IT1 US24)

ある特定船舶の連続した 1 区間 (出発港 → 到着港 + 出発・到着時刻)。
-}
module Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  ) where

import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode)

data CarrierMovement = CarrierMovement
  { departureLocation :: !UnLocode
  , arrivalLocation :: !UnLocode
  , departureTime :: !UTCTime
  , arrivalTime :: !UTCTime
  }
  deriving stock (Eq, Show)
