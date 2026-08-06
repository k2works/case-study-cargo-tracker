{-# LANGUAGE OverloadedStrings #-}

{- | Pricing BC 用インメモリ PricingRule Repository (US21, IT6)

Postgres 実装未整備の間、単一プロセスで動作するデモ用ルールセット。

デフォルトルール:
- JPY: baseRate 10000 + 100 円/km + 50 円/kg
- USD: baseRate 100 + 1 USD/km + 1 USD/kg

用途:
- ローカル開発 (Main で新規注入)
- 統合テストのデフォルトフィクスチャ
-}
module Cargotracker.Pricing.Infrastructure.InMemoryPricingRuleRepository
  ( newInMemoryPricingRuleRepository,
  ) where

import Cargotracker.Pricing.Application.Ports
  ( PricingRuleRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.PricingRule
  ( PricingRule,
    mkPricingRule,
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Currency (..),
  )

buildRule :: Currency -> Integer -> Integer -> Integer -> PricingRule
buildRule c base d w = case mkPricingRule c base d w of
  Right r -> r
  Left _ -> error "InMemoryPricingRuleRepository: rule seed invalid"

jpyRule, usdRule :: PricingRule
jpyRule = buildRule (Currency "JPY") 10000 100 50
usdRule = buildRule (Currency "USD") 100 1 1

newInMemoryPricingRuleRepository :: PricingRuleRepository IO
newInMemoryPricingRuleRepository =
  PricingRuleRepository
    { findByCurrency = \c -> pure $ case unCurrencyDemo c of
        "JPY" -> Just jpyRule
        "USD" -> Just usdRule
        _ -> Nothing
    }
  where
    unCurrencyDemo (Currency t) = t
