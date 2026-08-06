{-# LANGUAGE OverloadedStrings #-}

{- | Discount VO の単体テスト (US21, IT6)

割引率は 0-100% の整数百分率で保持する (data-model.md §設計判断 3 準拠、
浮動小数点回避)。Cost に適用すると `cost * (100 - rate) `div` 100` で
切り捨て計算した Cost を返す。

観点:
- 0-100% を受理
- 負値・101 以上は InvalidDiscountRate
- applyDiscount: 割引適用後の Cost を計算 (小数点切り捨て)
- applyDiscount: 通貨は元の Cost を維持
-}
module Pricing.Domain.Model.Value.DiscountSpec (spec) where

import Test.Hspec

import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.Discount
  ( Discount (..),
    applyDiscount,
    mkDiscount,
    noDiscount,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

jpy :: Currency
jpy = Currency "JPY"

spec :: Spec
spec = do
  describe "mkDiscount (US21)" $ do
    it "0% (下限) を受理する" $
      mkDiscount 0 `shouldBe` Right (Discount 0)

    it "100% (上限、全額割引) を受理する" $
      mkDiscount 100 `shouldBe` Right (Discount 100)

    it "30% を受理する" $
      mkDiscount 30 `shouldBe` Right (Discount 30)

    it "負値は InvalidDiscountRate" $
      mkDiscount (-1) `shouldBe` Left (InvalidDiscountRate (-1))

    it "101% は InvalidDiscountRate" $
      mkDiscount 101 `shouldBe` Left (InvalidDiscountRate 101)

  describe "noDiscount" $
    it "0% を表す (シンタックスシュガー)" $
      noDiscount `shouldBe` Discount 0

  describe "applyDiscount (US21 割引適用)" $ do
    let cost1000 = Cost 1000 jpy
        d10 = Discount 10 -- 10% 割引
        d100 = Discount 100 -- 全額割引
        d0 = Discount 0 -- 割引なし
    it "0% 割引は元の Cost を返す" $
      applyDiscount d0 cost1000 `shouldBe` Cost 1000 jpy

    it "10% 割引: 1000 × 90 / 100 = 900" $
      applyDiscount d10 cost1000 `shouldBe` Cost 900 jpy

    it "100% 割引: 全額無料になる" $
      applyDiscount d100 cost1000 `shouldBe` Cost 0 jpy

    it "小数点は切り捨て: 999 × 90 / 100 = 899 (899.1 → 899)" $
      applyDiscount d10 (Cost 999 jpy) `shouldBe` Cost 899 jpy

    it "通貨は変わらない" $ do
      let usd = Currency "USD"
      applyDiscount d10 (Cost 500 usd) `shouldBe` Cost 450 usd
