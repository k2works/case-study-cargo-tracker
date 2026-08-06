{-# LANGUAGE OverloadedStrings #-}

{- | Pricing BC 用インメモリ CurrencyRate Repository (US21, IT6)

デモ用の固定レート。有効期間は「常に有効」として非常に長い期間を設定する。

デフォルトレート:
- USD → JPY: 150 (2026-01-01 〜 2100-01-01)
- EUR → JPY: 165 (同期間)

その他の組合せは Nothing を返す (CurrencyRateNotFound になる)。
-}
module Cargotracker.Pricing.Infrastructure.InMemoryCurrencyRateRepository
  ( newInMemoryCurrencyRateRepository,
  ) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)

import Cargotracker.Pricing.Application.Ports
  ( CurrencyRateRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Currency (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate
  ( CurrencyRate,
    mkCurrencyRate,
  )

demoStart, demoEnd :: UTCTime
demoStart = UTCTime (fromGregorian 2026 1 1) (secondsToDiffTime 0)
demoEnd = UTCTime (fromGregorian 2100 1 1) (secondsToDiffTime 0)

buildRate :: Currency -> Currency -> Integer -> CurrencyRate
buildRate from to r = case mkCurrencyRate from to r demoStart demoEnd of
  Right x -> x
  Left _ -> error "InMemoryCurrencyRateRepository: rate seed invalid"

usdToJpy, eurToJpy :: CurrencyRate
usdToJpy = buildRate (Currency "USD") (Currency "JPY") 150
eurToJpy = buildRate (Currency "EUR") (Currency "JPY") 165

newInMemoryCurrencyRateRepository :: CurrencyRateRepository IO
newInMemoryCurrencyRateRepository =
  CurrencyRateRepository
    { findValidRate = \from to _now ->
        pure $ case (unCurrency' from, unCurrency' to) of
          ("USD", "JPY") -> Just usdToJpy
          ("EUR", "JPY") -> Just eurToJpy
          _ -> Nothing
    }
  where
    unCurrency' (Currency t) = t
