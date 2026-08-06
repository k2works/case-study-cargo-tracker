{- | Pricing BC の Discount 値オブジェクト (US21, IT6)

割引率を 0-100% の整数百分率で保持する。浮動小数点を避けるため、
Cost への適用は `cost * (100 - rate) `div` 100` で切り捨て計算する
(data-model.md §設計判断 3 準拠)。

法人契約割引 (data-model.md §shipper.discount_rate) や
プロモーション割引を統一的に扱うための Domain 型。
-}
module Cargotracker.Pricing.Domain.Model.Value.Discount
  ( Discount (..),
    mkDiscount,
    noDiscount,
    applyDiscount,
  ) where

import Cargotracker.Pricing.Domain.Model.Value.Cost (Cost (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

-- | 割引率 (0-100 の整数百分率)。0 = 割引なし、100 = 全額割引。
newtype Discount = Discount {unDiscount :: Integer}
  deriving stock (Eq, Show)

-- | Discount のスマートコンストラクタ。0-100 の範囲外は InvalidDiscountRate。
mkDiscount :: Integer -> Either DomainError Discount
mkDiscount r
  | r < 0 || r > 100 = Left (InvalidDiscountRate r)
  | otherwise = Right (Discount r)

-- | 割引なし (0%)。
noDiscount :: Discount
noDiscount = Discount 0

{- | Cost に割引を適用する。切り捨てで計算し、通貨は元の Cost を維持する。

`cost * (100 - rate) `div` 100`
-}
applyDiscount :: Discount -> Cost -> Cost
applyDiscount (Discount r) (Cost amount c) =
  Cost (amount * (100 - r) `div` 100) c
