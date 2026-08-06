{- | Voyage 集約ルート (IT1 US24)

特定船舶の運送区間集合 (連続した CarrierMovement のリスト)。
集約構築時に区間連続性 (前区間 arrival == 次区間 departure) を検証する。
-}
module Cargotracker.Routing.Domain.Model.Voyage
  ( Voyage (..),
    mkVoyage,
    updateMovements,
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

{- | 既存 Voyage の区間を差し替える (US25, IT2)。

入力 movements に対して mkVoyage と同じ連続性検証を適用し、
成功時は voyageVersion を +1 して返す (楽観ロックに使用)。
新規作成の mkVoyage と異なり、既存集約の同一性 (voyageNumber) を
保持する点が要点。
-}
updateMovements :: Voyage -> [CarrierMovement] -> Either DomainError Voyage
updateMovements existing newMovements
  | null newMovements =
      Left (LegContinuityViolation "at least 1 movement required")
  | not (isContinuous newMovements) =
      Left
        ( LegContinuityViolation
            ( "voyage "
                <> unVoyageNumber (voyageNumber existing)
                <> ": leg continuity broken"
            )
        )
  | otherwise =
      Right
        existing
          { carrierMovements = newMovements
          , voyageVersion = voyageVersion existing + 1
          }
