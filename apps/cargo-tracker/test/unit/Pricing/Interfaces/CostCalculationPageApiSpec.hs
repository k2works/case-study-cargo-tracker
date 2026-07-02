{-# LANGUAGE OverloadedStrings #-}

-- | CostCalculationPageApi の hspec-wai 統合テスト (US21, IT6)
module Pricing.Interfaces.CostCalculationPageApiSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Pricing.Application.Ports
  ( CurrencyRateRepository (..),
    PricingRuleRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.PricingRule
  ( PricingRule,
    mkPricingRule,
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Currency (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate
  ( mkCurrencyRate,
  )
import Cargotracker.Pricing.Interfaces.CostCalculationPageApi
  ( costCalculationApp,
  )
import Support.HspecWaiJa (bodyContainsText)

--------------------------------------------------------------------------------
-- フィクスチャ
--------------------------------------------------------------------------------

jpyRule :: PricingRule
jpyRule = case mkPricingRule (Currency "JPY") 10000 100 50 of
  Right r -> r
  Left _ -> error "unreachable"

usdRule :: PricingRule
usdRule = case mkPricingRule (Currency "USD") 100 1 1 of
  Right r -> r
  Left _ -> error "unreachable"

jpyRuleRepo :: PricingRuleRepository IO
jpyRuleRepo =
  PricingRuleRepository
    { findByCurrency = \c ->
        pure $
          if c == Currency "JPY"
            then Just jpyRule
            else Nothing
    }

usdRuleRepo :: PricingRuleRepository IO
usdRuleRepo =
  PricingRuleRepository
    { findByCurrency = \c ->
        pure $
          if c == Currency "USD"
            then Just usdRule
            else Nothing
    }

emptyRateRepo :: CurrencyRateRepository IO
emptyRateRepo =
  CurrencyRateRepository {findValidRate = \_ _ _ -> pure Nothing}

usdRateRepo :: CurrencyRateRepository IO
usdRateRepo =
  CurrencyRateRepository
    { findValidRate = \from to _ ->
        pure $
          if from == Currency "USD" && to == Currency "JPY"
            then case mkCurrencyRate
              (Currency "USD")
              (Currency "JPY")
              150
              (UTCTime (fromGregorian 2026 1 1) 0)
              (UTCTime (fromGregorian 2027 1 1) 0) of
              Right r -> Just r
              Left _ -> Nothing
            else Nothing
    }

appJpy :: IO Network.Wai.Application
appJpy = pure (costCalculationApp jpyRuleRepo emptyRateRepo)

appUsd :: IO Network.Wai.Application
appUsd = pure (costCalculationApp usdRuleRepo usdRateRepo)

--------------------------------------------------------------------------------
-- spec
--------------------------------------------------------------------------------

spec :: Spec
spec = describe "CostCalculationPageApi (US21)" $ do
  describe "GET /pricing/calculate" $
    with appJpy $ do
      it "200 を返す" $
        get "/pricing/calculate" `shouldRespondWith` 200

      it "form action=/pricing/calculate を含む" $
        get "/pricing/calculate"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "action=\"/pricing/calculate\""
            }

  describe "POST /pricing/calculate 正常系 (JPY 基準)" $
    with appJpy $
      it "General / 距離 10 / 重量 5 / 割引 0 → 11250 JPY" $
        request
          "POST"
          "/pricing/calculate"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "cargoCategory=General&distanceKm=10&weightKg=5&baseCurrency=JPY&targetCurrency=JPY&discountRate=0"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "11250"
            }

  describe "POST /pricing/calculate 割引適用" $
    with appJpy $
      it "20% 割引 → 9000" $
        request
          "POST"
          "/pricing/calculate"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "cargoCategory=General&distanceKm=10&weightKg=5&baseCurrency=JPY&targetCurrency=JPY&discountRate=20"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "9000"
            }

  describe "POST /pricing/calculate エラー系" $ do
    with appJpy $
      it "貨物カテゴリ不正 → エラーメッセージ" $
        request
          "POST"
          "/pricing/calculate"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "cargoCategory=Unknown&distanceKm=10&weightKg=5&baseCurrency=JPY&targetCurrency=JPY&discountRate=0"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "未対応の貨物カテゴリ"
            }

    with appJpy $
      it "PricingRule 未登録通貨 → PricingRuleNotFound を含む" $
        request
          "POST"
          "/pricing/calculate"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "cargoCategory=General&distanceKm=10&weightKg=5&baseCurrency=EUR&targetCurrency=EUR&discountRate=0"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "PricingRuleNotFound"
            }

  describe "POST /pricing/calculate 通貨換算" $
    with appUsd $
      it "USD 基準 → JPY 対象で 17250" $
        request
          "POST"
          "/pricing/calculate"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "cargoCategory=General&distanceKm=10&weightKg=5&baseCurrency=USD&targetCurrency=JPY&discountRate=0"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "17250"
            }
