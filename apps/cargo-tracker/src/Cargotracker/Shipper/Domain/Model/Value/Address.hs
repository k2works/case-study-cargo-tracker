{- | 住所値オブジェクト (IT1 US02/03)

最大 500 文字 (domain-model.md 規約)。
-}
module Cargotracker.Shipper.Domain.Model.Value.Address
  ( Address (..),
    mkAddress,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype Address = Address {unAddress :: Text}
  deriving stock (Eq, Show)

mkAddress :: Text -> Either DomainError Address
mkAddress t
  | T.null t = Left (InvalidShipperId "empty address")
  | T.length t > 500 = Left (InvalidShipperId "address too long (max 500)")
  | otherwise = Right (Address t)
