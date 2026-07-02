{-# LANGUAGE OverloadedStrings #-}

{- | CalculateShippingCostCommand の単体テスト (US21, IT6)

観点:
- 基準通貨=対象通貨の正常系: 割引適用まで
- 基準通貨≠対象通貨の正常系: CurrencyRate.convert で通貨換算
- PricingRule が見つからない → PricingRuleNotFound
- CurrencyRate が見つからない → CurrencyRateNotFound
- CurrencyRate が期限外 → CurrencyRateExpired
- 距離・重量負値 → InvalidCost (Domain の calculate 側で発生)
-}
module Pricing.Application.CalculateShippingCostCommandSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Pricing.Application.CalculateShippingCostCommand
  ( CalculateShippingCostInput (..),
    execute,
  )
import Cargotracker.Pricing.Application.Ports
  ( CurrencyRateRepository (..),
    PricingRuleRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.PricingRule
  ( CargoCategory (..),
    PricingRule (..),
    mkPricingRule,
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate
  ( CurrencyRate (..),
    mkCurrencyRate,
  )
import Cargotracker.Pricing.Domain.Model.Value.Discount
  ( Discount (..),
    noDiscount,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

--------------------------------------------------------------------------------
-- フィクスチャ
--------------------------------------------------------------------------------

jpy :: Currency
jpy = Currency "JPY"

usd :: Currency
usd = Currency "USD"

now :: UTCTime
now = UTCTime (fromGregorian 2026 7 15) (secondsToDiffTime 0)

-- baseRate 10000 JPY / distance 100 / weight 50
sampleRule :: PricingRule
sampleRule = case mkPricingRule jpy 10000 100 50 of
  Right r -> r
  Left _ -> error "unreachable"

-- 1 USD = 150 JPY (2026-07-01 〜 2026-08-01)
usdToJpyRate :: CurrencyRate
usdToJpyRate = case mkCurrencyRate usd jpy 150 (UTCTime (fromGregorian 2026 7 1) 0) (UTCTime (fromGregorian 2026 8 1) 0) of
  Right r -> r
  Left _ -> error "unreachable"

ruleRepoOf :: [(Currency, PricingRule)] -> PricingRuleRepository IO
ruleRepoOf assoc =
  PricingRuleRepository {findByCurrency = \c -> pure (lookup c assoc)}

rateRepoOf :: [((Currency, Currency), CurrencyRate)] -> CurrencyRateRepository IO
rateRepoOf assoc =
  CurrencyRateRepository
    { findValidRate = \from to _ -> pure (lookup (from, to) assoc)
    }

sampleInput :: CalculateShippingCostInput
sampleInput =
  CalculateShippingCostInput
    { inputCargoCategory = General
    , inputDistanceKm = 10
    , inputWeightKg = 5
    , inputBaseCurrency = jpy
    , inputTargetCurrency = jpy
    , inputDiscount = noDiscount
    , inputNow = now
    }

spec :: Spec
spec = describe "CalculateShippingCostCommand.execute (US21)" $ do
  it "基準通貨=対象通貨、割引なし: PricingRule.calculate と等価 (11250 JPY)" $ do
    let ruleRepo = ruleRepoOf [(jpy, sampleRule)]
        rateRepo = rateRepoOf []
    result <- execute ruleRepo rateRepo sampleInput
    result `shouldBe` Right (Cost 11250 jpy)

  it "10% 割引適用: 11250 → 10125 (切り捨て)" $ do
    let ruleRepo = ruleRepoOf [(jpy, sampleRule)]
        rateRepo = rateRepoOf []
        input = sampleInput {inputDiscount = Discount 10}
    result <- execute ruleRepo rateRepo input
    result `shouldBe` Right (Cost 10125 jpy)

  it "Hazardous 貨物 (1.5 倍) + 20% 割引: 16875 → 13500" $ do
    let ruleRepo = ruleRepoOf [(jpy, sampleRule)]
        rateRepo = rateRepoOf []
        input =
          sampleInput
            { inputCargoCategory = Hazardous
            , inputDiscount = Discount 20
            }
    result <- execute ruleRepo rateRepo input
    result `shouldBe` Right (Cost 13500 jpy)

  it "USD 基準 → JPY 換算 (1 USD PricingRule + 1 USD=150 JPY rate)" $ do
    let usdRule = case mkPricingRule usd 100 1 1 of
          Right r -> r
          Left _ -> error "unreachable"
        ruleRepo = ruleRepoOf [(usd, usdRule)]
        rateRepo = rateRepoOf [((usd, jpy), usdToJpyRate)]
        input =
          sampleInput
            { inputBaseCurrency = usd
            , inputTargetCurrency = jpy
            }
    -- General 貨物: 100 + 1*10 + 1*5 = 115 USD → 115 * 150 = 17250 JPY
    result <- execute ruleRepo rateRepo input
    result `shouldBe` Right (Cost 17250 jpy)

  it "PricingRule 未登録は PricingRuleNotFound" $ do
    let ruleRepo = ruleRepoOf []
        rateRepo = rateRepoOf []
    result <- execute ruleRepo rateRepo sampleInput
    result `shouldBe` Left (PricingRuleNotFound "JPY")

  it "USD → JPY レート未登録は CurrencyRateNotFound" $ do
    let usdRule = case mkPricingRule usd 100 1 1 of
          Right r -> r
          Left _ -> error "unreachable"
        ruleRepo = ruleRepoOf [(usd, usdRule)]
        rateRepo = rateRepoOf []
        input = sampleInput {inputBaseCurrency = usd, inputTargetCurrency = jpy}
    result <- execute ruleRepo rateRepo input
    result `shouldBe` Left (CurrencyRateNotFound "USD" "JPY")

  it "距離負値は InvalidCost (Domain calculate 側)" $ do
    let ruleRepo = ruleRepoOf [(jpy, sampleRule)]
        rateRepo = rateRepoOf []
        input = sampleInput {inputDistanceKm = -1}
    result <- execute ruleRepo rateRepo input
    result `shouldBe` Left (InvalidCost (-1))
