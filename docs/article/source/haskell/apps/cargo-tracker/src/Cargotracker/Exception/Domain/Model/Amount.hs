{- | Exception BC の金額 VO (US20, IT7)

損害額 (DamageException) / 損失額 (LossException) を保持する VO。
最小通貨単位を Integer で保持し (data-model.md §設計判断 3 準拠)、
Rule 4 遵守のため currency は Text (ISO 4217 大文字 3 文字) で保持する
(Pricing BC の Currency 型に依存しない)。

Pricing BC の Cost VO とは独立した設計。CalculateShippingCostCommand の
インタフェースへ渡す際は Text-DTO 経由で変換する (Cross-BC helper 経由、将来対応)。
-}
module Cargotracker.Exception.Domain.Model.Amount
  ( Amount (..),
    mkAmount,
  ) where

import Data.Char (isAsciiUpper)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Amount = Amount
  { amValue :: !Integer
  -- ^ 最小通貨単位 (>= 0)
  , amCurrency :: !Text
  -- ^ ISO 4217 大文字 3 文字 (JPY / USD / EUR 等)
  }
  deriving stock (Eq, Show)

{- | Amount のスマートコンストラクタ。

* value < 0 → InvalidCost value
* currency が 3 文字大文字英字でない → InvalidCurrency currency
-}
mkAmount :: Integer -> Text -> Either DomainError Amount
mkAmount value currency
  | value < 0 = Left (InvalidCost value)
  | not (validCurrency currency) = Left (InvalidCurrency currency)
  | otherwise = Right (Amount value currency)
  where
    validCurrency c = T.length c == 3 && T.all isAsciiUpper c
