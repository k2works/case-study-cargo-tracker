{- | Voyage 集約ルート (IT1 US24)

特定船舶の運送区間集合 (連続した CarrierMovement のリスト)。
集約構築時に区間連続性 (前区間 arrival == 次区間 departure) を検証する。
-}
module Cargotracker.Routing.Domain.Model.Voyage
  ( Voyage (..),
    mkVoyage,
  ) where

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber, unVoyageNumber)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Voyage = Voyage
  { voyageNumber :: !VoyageNumber
  , carrierMovements :: ![CarrierMovement]
  , voyageVersion :: !Int
  }
  deriving stock (Eq, Show)

{- | Voyage を構築する。
- 区間 0 件は不可
- 区間 N >= 2 の場合は連続性を検証する (前 arrivalLocation == 次 departureLocation)
-}
mkVoyage :: VoyageNumber -> [CarrierMovement] -> Either DomainError Voyage
mkVoyage vn movements
  | null movements =
      Left (LegContinuityViolation "at least 1 movement required")
  | not (isContinuous movements) =
      Left
        ( LegContinuityViolation
            ("voyage " <> unVoyageNumber vn <> ": leg continuity broken")
        )
  | otherwise =
      Right
        Voyage
          { voyageNumber = vn
          , carrierMovements = movements
          , voyageVersion = 1
          }

isContinuous :: [CarrierMovement] -> Bool
isContinuous [] = True
isContinuous [_] = True
isContinuous (a : b : rest) =
  arrivalLocation a == departureLocation b
    && isContinuous (b : rest)
