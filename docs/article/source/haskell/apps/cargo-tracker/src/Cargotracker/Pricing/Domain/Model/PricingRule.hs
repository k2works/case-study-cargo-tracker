{- | Pricing BC の PricingRule 集約 (US21, IT6)

輸送料金の計算式を保持する集約。1 通貨 = 1 ルールを想定し、
`baseRate + distanceRate * distance_km + weightRate * weight_kg` に貨物種別の割増を
適用した Cost を返す。

Cross-BC 参照ポリシー (ADR-0004 Rule 4):

- Booking BC の CargoType (`General | Hazardous data | Refrigerated data`) を
  直接 import しない
- 本 BC 独自の `CargoCategory` (単純な列挙型) を定義し、Cross-BC helper で
  Booking.CargoType → CargoCategory に変換して受け取る

計算精度:

- 割増レートは Integer 演算に落とし込むため 100 分率で保持 (100 = 等倍、130 = 1.3 倍)
- `baseAmount * category100 `div` 100` で丸め (切り捨て)、負値化しない
-}
module Cargotracker.Pricing.Domain.Model.PricingRule
  ( CargoCategory (..),
    PricingRule (..),
    mkPricingRule,
    calculate,
    categoryMultiplier100,
  ) where

import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

{- | Pricing BC 独自の貨物カテゴリ (Cross-BC helper で Booking.CargoType から変換)。
Rule 4 準拠のため、他 BC の型を Domain 内に import しない。
-}
data CargoCategory
  = General
  | Hazardous
  | Refrigerated
  deriving stock (Eq, Show)

{- | 輸送料金計算ルール。すべての金額は最小通貨単位の Integer で保持する。

- baseRate: 基本料金 (固定額)
- distanceRate: 距離 1 km あたりの追加料金
- weightRate: 重量 1 kg あたりの追加料金
-}
data PricingRule = PricingRule
  { prCurrency :: !Currency
  , prBaseRate :: !Integer
  , prDistanceRatePerKm :: !Integer
  , prWeightRatePerKg :: !Integer
  }
  deriving stock (Eq, Show)

{- | PricingRule のスマートコンストラクタ。全レートは非負である必要がある。
最初に見つかった負値を InvalidCost で返す。
-}
mkPricingRule ::
  Currency ->
  Integer -> -- baseRate
  Integer -> -- distanceRate
  Integer -> -- weightRate
  Either DomainError PricingRule
mkPricingRule c base distance weight
  | base < 0 = Left (InvalidCost base)
  | distance < 0 = Left (InvalidCost distance)
  | weight < 0 = Left (InvalidCost weight)
  | otherwise =
      Right
        PricingRule
          { prCurrency = c
          , prBaseRate = base
          , prDistanceRatePerKm = distance
          , prWeightRatePerKg = weight
          }

-- | 貨物種別に応じた 100 分率の割増係数。100 = 等倍、130 = 1.3 倍、150 = 1.5 倍。
categoryMultiplier100 :: CargoCategory -> Integer
categoryMultiplier100 General = 100
categoryMultiplier100 Refrigerated = 130
categoryMultiplier100 Hazardous = 150

{- | 料金を計算する純粋関数。

観点:
  * 距離・重量が負値なら `InvalidCost` を返す (呼出側の Weight VO で通常防ぐが二重防御)
  * 貨物種別による割増は 100 分率で乗じた後 `div 100` で切り捨て
-}
calculate ::
  PricingRule ->
  CargoCategory ->
  Integer -> -- distance_km
  Integer -> -- weight_kg
  Either DomainError Cost
calculate rule cat distanceKm weightKg
  | distanceKm < 0 = Left (InvalidCost distanceKm)
  | weightKg < 0 = Left (InvalidCost weightKg)
  | otherwise =
      let baseAmount =
            prBaseRate rule
              + prDistanceRatePerKm rule * distanceKm
              + prWeightRatePerKg rule * weightKg
          adjusted = baseAmount * categoryMultiplier100 cat `div` 100
       in Right (Cost adjusted (prCurrency rule))
