{- | 航海スケジュール更新コマンド (US25, IT2)

業務フロー:
1. VoyageNumber と新しい区間集合を受け取り、`mkVoyageNumber` で検証
2. `findByVoyageNumber` で既存航海を取得 (なければ `BookingNotFound` 相当の
   エラー — 航海固有のエラー型は IT3 で導入)
3. 入力区間を `CarrierMovement` に変換して `Voyage.updateMovements` で
   連続性検証 + version インクリメント
4. `VoyageRepository.updateVoyage` で永続化 (Infrastructure 側で全置換 +
   `withTransaction` ラップ)

ADR 0002 T-01: トランザクション境界は Infrastructure 層に集約する。
Domain 層 (Voyage.updateMovements) は純粋関数。
-}
module Cargotracker.Routing.Application.UpdateVoyageCommand
  ( UpdateVoyageInput (..),
    execute,
  ) where

import Data.Text (Text)

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Application.RegisterVoyageCommand
  ( CarrierMovementInput (..),
  )
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage, updateMovements)
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data UpdateVoyageInput = UpdateVoyageInput
  { inputVoyageNumber :: !Text
  , inputMovements :: ![CarrierMovementInput]
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  VoyageRepository m ->
  UpdateVoyageInput ->
  m (Either DomainError Voyage)
execute repo input = case mkVoyageNumber (inputVoyageNumber input) of
  Left e -> pure (Left e)
  Right vn -> do
    mExisting <- findByVoyageNumber repo vn
    case mExisting of
      Nothing ->
        pure
          ( Left
              ( InvalidVoyageNumber
                  ("voyage not found: " <> unVoyageNumber vn)
              )
          )
      Just existing -> case traverse buildMovement (inputMovements input) of
        Left e -> pure (Left e)
        Right movements -> case updateMovements existing movements of
          Left e -> pure (Left e)
          Right updated -> do
            saveResult <- updateVoyage repo updated
            case saveResult of
              Left e -> pure (Left e)
              Right () -> pure (Right updated)
  where
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
