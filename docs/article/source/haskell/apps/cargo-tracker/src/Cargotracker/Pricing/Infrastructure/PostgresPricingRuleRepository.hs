{-# LANGUAGE OverloadedStrings #-}

{- | Pricing BC の PostgreSQL 実装 PricingRuleRepository (US21 Phase 8, IT6)

pricing_rule テーブルへの SELECT (currency 検索)。

T-02 準拠: Tx 境界は張らない (Application 層で管理)。
-}
module Cargotracker.Pricing.Infrastructure.PostgresPricingRuleRepository
  ( newPostgresPricingRuleRepository,
  ) where

import Data.Text (Text)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    query,
  )

import Cargotracker.Pricing.Application.Ports
  ( PricingRuleRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.PricingRule
  ( PricingRule (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost (Currency (..))

newPostgresPricingRuleRepository :: Connection -> PricingRuleRepository IO
newPostgresPricingRuleRepository conn =
  PricingRuleRepository
    { findByCurrency = findByCurrencyImpl conn
    }

-- 1 行の Text 表現 (currency / base_rate / distance_rate / weight_rate)
type PricingRuleRow = (Text, Integer, Integer, Integer)

findByCurrencyImpl :: Connection -> Currency -> IO (Maybe PricingRule)
findByCurrencyImpl conn (Currency code) = do
  rows <-
    query
      conn
      "SELECT currency, base_rate, distance_rate_per_km, weight_rate_per_kg \
      \ FROM pricing_rule WHERE currency = ? LIMIT 1"
      (Only code) ::
      IO [PricingRuleRow]
  pure $ case rows of
    ((c, base, d, w) : _) ->
      Just
        PricingRule
          { prCurrency = Currency c
          , prBaseRate = base
          , prDistanceRatePerKm = d
          , prWeightRatePerKg = w
          }
    [] -> Nothing
