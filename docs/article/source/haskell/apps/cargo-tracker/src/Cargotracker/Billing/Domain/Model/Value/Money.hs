{- | 金額 VO (US23, IT8, Billing BC)

data-model.md 設計判断 3 に従い、最小通貨単位の Integer で保持する
(`Double` は金額計算に使用しない)。通貨は ISO 4217 の 3 文字大文字コード。

Pricing BC の `Cost` と同型だが、Rule 4 (BC 間 Domain 直接参照禁止) に
従い Billing BC 専用の VO として定義する。
-}
module Cargotracker.Billing.Domain.Model.Value.Money
  ( Money (..),
    mkMoney,
    zeroMoney,
  ) where

import Data.Char (isAsciiUpper)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Money = Money
  { moneyAmount :: !Integer
  -- ^ 最小通貨単位 (JPY なら円、USD ならセント)
  , moneyCurrency :: !Text
  -- ^ ISO 4217 3 文字大文字コード
  }
  deriving stock (Eq, Show)

mkMoney :: Integer -> Text -> Either DomainError Money
mkMoney amount currency
  | amount < 0 = Left (InvalidCost amount)
  | not validCurrency = Left (InvalidCurrency currency)
  | otherwise = Right (Money amount currency)
  where
    validCurrency = T.length currency == 3 && T.all isAsciiUpper currency

zeroMoney :: Text -> Either DomainError Money
zeroMoney = mkMoney 0
