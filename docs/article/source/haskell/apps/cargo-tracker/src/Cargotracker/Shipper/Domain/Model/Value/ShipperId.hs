{- | Shipper 識別子 (IT1 US02/03)

採番ルール: `SHP-XXXXXX` (英数字 6 文字)。
完全な業務 ID 体系は IT2 で確定するが、IT1 では構造のみ検証する。
-}
module Cargotracker.Shipper.Domain.Model.Value.ShipperId
  ( ShipperId (..),
    mkShipperId,
  ) where

import Data.Char (isAsciiUpper, isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype ShipperId = ShipperId {unShipperId :: Text}
  deriving stock (Eq, Show)

mkShipperId :: Text -> Either DomainError ShipperId
mkShipperId t = case T.stripPrefix "SHP-" t of
  Just rest
    | T.length rest == 6 && T.all isAlphaNum rest ->
        Right (ShipperId t)
  _ -> Left (InvalidShipperId "expected SHP-XXXXXX")
  where
    isAlphaNum c = isAsciiUpper c || isDigit c
