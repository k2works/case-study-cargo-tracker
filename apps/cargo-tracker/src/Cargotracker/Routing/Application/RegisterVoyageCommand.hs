{- | 航海スケジュール登録コマンド (IT1 US24 4.2)

入力検証 → CarrierMovement リスト変換 → mkVoyage (区間連続性検証) →
重複検出 → 永続化。
-}
module Cargotracker.Routing.Application.RegisterVoyageCommand
  ( RegisterVoyageInput (..),
    CarrierMovementInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber,
    mkVoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (mkVoyage)
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data CarrierMovementInput = CarrierMovementInput
  { inputDeparture :: !Text
  , inputArrival :: !Text
  , inputDepartureTime :: !UTCTime
  , inputArrivalTime :: !UTCTime
  }
  deriving stock (Eq, Show)

data RegisterVoyageInput = RegisterVoyageInput
  { inputVoyageNumber :: !Text
  , inputMovements :: ![CarrierMovementInput]
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  VoyageRepository m ->
  RegisterVoyageInput ->
  m (Either DomainError VoyageNumber)
execute repo input = case validate input of
  Left e -> pure (Left e)
  Right voyage -> do
    let vn = case mkVoyageNumber (inputVoyageNumber input) of
          Right v -> v
          Left _ -> error "unreachable: validated above"
    mExisting <- findByVoyageNumber repo vn
    case mExisting of
      Just _ ->
        pure
          ( Left
              ( ConcurrentModification
                  ("duplicate voyage: " <> unVoyageNumber vn)
              )
          )
      Nothing -> do
        saveVoyage repo voyage
        pure (Right vn)
  where
    validate i = do
      vn <- mkVoyageNumber (inputVoyageNumber i)
      movements <- traverse buildMovement (inputMovements i)
      mkVoyage vn movements

    buildMovement m = do
      dep <- mkUnLocode (inputDeparture m)
      arr <- mkUnLocode (inputArrival m)
      Right
        CarrierMovement
          { departureLocation = dep
          , arrivalLocation = arr
          , departureTime = inputDepartureTime m
          , arrivalTime = inputArrivalTime m
          }
