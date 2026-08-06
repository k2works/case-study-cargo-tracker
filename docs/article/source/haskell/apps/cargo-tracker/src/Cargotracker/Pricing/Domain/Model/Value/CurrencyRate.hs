{- | Pricing BC の CurrencyRate エンティティ (US21, IT6)

通貨換算レート (1 単位の from 通貨 = rate 単位の to 通貨) を保持する。
有効期間 (validFrom / validTo) を持ち、期間内のみ convert 可能。

data-model.md §currency_rate に対応 (T5-11/6.2 で追記予定)。

設計判断:

- rate は整数の Integer (小数点を扱う場合は上位で 10^n スケール済みを想定)
- fromCurrency == toCurrency (同一通貨) は自明なため InvalidCurrency で弾く
- validFrom < validTo を必須にし、境界は `>= validFrom && < validTo` で判定
- 過去日付・未来日付ともに Domain 層は扱わない (期限判定は現在時刻 UTCTime で行う)
-}
module Cargotracker.Pricing.Domain.Model.Value.CurrencyRate
  ( CurrencyRate (..),
    mkCurrencyRate,
    isRateValidAt,
    convert,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency (..),
    unCurrency,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

-- | 通貨換算レート。1 単位の crFromCurrency = crRate 単位の crToCurrency。
data CurrencyRate = CurrencyRate
  { crFromCurrency :: !Currency
  , crToCurrency :: !Currency
  , crRate :: !Integer
  , crValidFrom :: !UTCTime
  , crValidTo :: !UTCTime
  }
  deriving stock (Eq, Show)

{- | CurrencyRate のスマートコンストラクタ。

- 同一通貨は InvalidCurrency (換算不要)
- 負のレートは InvalidCost
- validFrom >= validTo は InvalidCurrencyRatePeriod
-}
mkCurrencyRate ::
  Currency ->
  Currency ->
  Integer -> -- rate (must be > 0)
  UTCTime ->
  UTCTime ->
  Either DomainError CurrencyRate
mkCurrencyRate fromC toC rate vFrom vTo
  | fromC == toC = Left (InvalidCurrency (unCurrency fromC))
  | rate < 0 = Left (InvalidCost rate)
  | vFrom >= vTo = Left InvalidCurrencyRatePeriod
  | otherwise =
      Right
        CurrencyRate
          { crFromCurrency = fromC
          , crToCurrency = toC
          , crRate = rate
          , crValidFrom = vFrom
          , crValidTo = vTo
          }

{- | 指定時刻 `now` にレートが有効かを判定する。

境界: `now >= validFrom && now < validTo` で扱う。
validTo ちょうどは無効 (次期レートに切り替わる想定)。
-}
isRateValidAt :: UTCTime -> CurrencyRate -> Bool
isRateValidAt now r = now >= crValidFrom r && now < crValidTo r

{- | Cost を crToCurrency に換算する。

- 期限外なら CurrencyRateExpired
- Cost.costCurrency が crFromCurrency と異なるなら CurrencyMismatch
- 換算式: `amount * rate` (小数演算なし)
-}
convert :: CurrencyRate -> UTCTime -> Cost -> Either DomainError Cost
convert r now (Cost amount c)
  | not (isRateValidAt now r) = Left CurrencyRateExpired
  | c /= crFromCurrency r =
      Left
        ( CurrencyMismatch
            (unCurrency c)
            (unCurrency (crFromCurrency r))
        )
  | otherwise = Right (Cost (amount * crRate r) (crToCurrency r))
