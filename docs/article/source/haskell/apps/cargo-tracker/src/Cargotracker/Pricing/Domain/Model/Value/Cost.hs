{- | Pricing BC の Cost 値オブジェクト (US21, IT6)

輸送料金を「金額 (最小通貨単位の Integer) + 通貨 (ISO 4217 3 文字)」の組で表現する。

設計判断 (data-model.md §設計判断 3 に準拠):

- 金額は最小通貨単位 (JPY=円、USD=セント) の `Integer`。浮動小数点は使わない
- 負の金額は `mkCost` / 演算結果ともに `InvalidCost` として弾く
- 通貨コードは `newtype Currency = Currency Text` (ISO 4217 3 文字大文字)
- 異通貨同士の演算は `CurrencyMismatch` を返す (Domain 純粋関数、副作用なし)

T-03 (IT2) 準拠: 全関数純粋、DB / IO 依存なし。
-}
module Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Currency (..),
    Cost (..),
    mkCurrency,
    mkCost,
    zeroCost,
    addCost,
    subCost,
  ) where

import Data.Char (isAsciiUpper)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

-- | ISO 4217 通貨コード (例: JPY / USD / EUR)。3 文字大文字のみ。
newtype Currency = Currency {unCurrency :: Text}
  deriving stock (Eq, Ord, Show)

{- | 通貨コードのスマートコンストラクタ。

3 文字かつすべて ASCII 大文字であることを検証する。
実際の ISO 4217 マスタとの照合は Application 層 (CurrencyRateRepository) が担う。
-}
mkCurrency :: Text -> Either DomainError Currency
mkCurrency t
  | T.length t == 3 && T.all isAsciiUpper t = Right (Currency t)
  | otherwise = Left (InvalidCurrency t)

-- | 輸送料金 (金額 + 通貨)。金額は最小通貨単位で保持する。
data Cost = Cost
  { costAmount :: !Integer
  , costCurrency :: !Currency
  }
  deriving stock (Eq, Show)

{- | Cost のスマートコンストラクタ。

負の金額は `InvalidCost` として弾く。0 は許容 (無料料金 or 満額割引の表現)。
-}
mkCost :: Integer -> Currency -> Either DomainError Cost
mkCost amount c
  | amount < 0 = Left (InvalidCost amount)
  | otherwise = Right (Cost amount c)

-- | 指定通貨の 0 コスト。
zeroCost :: Currency -> Cost
zeroCost = Cost 0

{- | 同通貨同士を加算する。

異通貨は `CurrencyMismatch` として弾く。
オーバーフローは Integer なので発生しない (任意精度)。
-}
addCost :: Cost -> Cost -> Either DomainError Cost
addCost a b
  | costCurrency a /= costCurrency b =
      Left
        ( CurrencyMismatch
            (unCurrency (costCurrency a))
            (unCurrency (costCurrency b))
        )
  | otherwise = Right (Cost (costAmount a + costAmount b) (costCurrency a))

{- | 同通貨同士を減算する。

- 異通貨は `CurrencyMismatch`
- 被減数 < 減数の場合は結果が負値になるため `InvalidCost` (負値の Cost を作らせない)
-}
subCost :: Cost -> Cost -> Either DomainError Cost
subCost a b
  | costCurrency a /= costCurrency b =
      Left
        ( CurrencyMismatch
            (unCurrency (costCurrency a))
            (unCurrency (costCurrency b))
        )
  | otherwise =
      let diff = costAmount a - costAmount b
       in if diff < 0
            then Left (InvalidCost diff)
            else Right (Cost diff (costCurrency a))
