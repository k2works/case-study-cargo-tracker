{- | 航海番号 (IT1 US24)

業務上の識別子。1-20 文字の任意文字列 (data-model.md §voyage 規約)。
-}
module Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber (..),
    mkVoyageNumber,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype VoyageNumber = VoyageNumber {unVoyageNumber :: Text}
  deriving stock (Eq, Show, Ord)

mkVoyageNumber :: Text -> Either DomainError VoyageNumber
mkVoyageNumber t
  | T.null t = Left (InvalidVoyageNumber "empty")
  | T.length t > 20 = Left (InvalidVoyageNumber "too long (max 20)")
  | otherwise = Right (VoyageNumber t)
