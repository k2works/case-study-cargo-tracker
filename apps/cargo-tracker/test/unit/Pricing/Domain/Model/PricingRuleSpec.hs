{-# LANGUAGE OverloadedStrings #-}

{- | PricingRule 集約 + calculate 関数の単体テスト (US21, IT6)

料金計算は次の式で行う:

  cost = baseRate + distanceRate * distance_km + weightRate * weight_kg

貨物種別 (CargoType) に応じてルールを選択する (General / Hazardous / Refrigerated)。
全ての金額は最小通貨単位の Integer で保持し、浮動小数点は避ける。

観点:
- General 貨物の基本計算
- Hazardous / Refrigerated 貨物には割増レートを適用
- 距離 0 / 重量 0 は baseRate のみ
- 負の距離・負の重量は InvalidCost
- Currency 不整合は起きない (PricingRule 内で単一通貨)
-}
module Pricing.Domain.Model.PricingRuleSpec (spec) where

import Test.Hspec

import Cargotracker.Pricing.Domain.Model.PricingRule
  ( CargoCategory (..),
    PricingRule (..),
    calculate,
    mkPricingRule,
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency (..),
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

jpy :: Currency
jpy = Currency "JPY"

-- baseRate 10000 円 / 距離 100 円/km / 重量 50 円/kg のルール (General)
sampleRule :: PricingRule
sampleRule = case mkPricingRule jpy 10000 100 50 of
  Right r -> r
  Left _ -> error "unreachable"

spec :: Spec
spec = do
  describe "mkPricingRule (US21)" $ do
    it "0 以上のレートを受理する" $
      mkPricingRule jpy 0 0 0 `shouldSatisfy` \case
        Right _ -> True
        _ -> False

    it "負の baseRate は InvalidCost" $
      mkPricingRule jpy (-1) 100 50 `shouldBe` Left (InvalidCost (-1))

    it "負の distanceRate は InvalidCost" $
      mkPricingRule jpy 10000 (-1) 50 `shouldBe` Left (InvalidCost (-1))

    it "負の weightRate は InvalidCost" $
      mkPricingRule jpy 10000 100 (-1) `shouldBe` Left (InvalidCost (-1))

  describe "calculate (US21 基本計算式)" $ do
    it "距離 0 km / 重量 0 kg なら baseRate のみ" $
      calculate sampleRule General 0 0 `shouldSatisfy` \case
        Right c -> costAmount c == 10000 && costCurrency c == jpy
        _ -> False

    it "General 貨物: 10 km / 5 kg = 10000 + 100*10 + 50*5 = 11250" $
      calculate sampleRule General 10 5 `shouldSatisfy` \case
        Right c -> costAmount c == 11250
        _ -> False

    it "Hazardous 貨物: 危険物割増 1.5 倍 = 10000 + 1500 + 375 = ?" $
      -- General 11250 × 1.5 = 16875
      calculate sampleRule Hazardous 10 5 `shouldSatisfy` \case
        Right c -> costAmount c == 16875
        _ -> False

    it "Refrigerated 貨物: 冷凍割増 1.3 倍 = General 11250 × 1.3 = 14625" $
      calculate sampleRule Refrigerated 10 5 `shouldSatisfy` \case
        Right c -> costAmount c == 14625
        _ -> False

    it "距離負値は InvalidCost" $
      calculate sampleRule General (-1) 5 `shouldBe` Left (InvalidCost (-1))

    it "重量負値は InvalidCost" $
      calculate sampleRule General 10 (-1) `shouldBe` Left (InvalidCost (-1))

    it "大量荷物 (1000 km / 5000 kg) でも Integer で計算可能" $
      -- 10000 + 100*1000 + 50*5000 = 10000 + 100000 + 250000 = 360000
      calculate sampleRule General 1000 5000 `shouldSatisfy` \case
        Right c -> costAmount c == 360000
        _ -> False
