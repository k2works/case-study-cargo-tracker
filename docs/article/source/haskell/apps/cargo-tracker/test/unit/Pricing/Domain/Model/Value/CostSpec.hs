{-# LANGUAGE OverloadedStrings #-}

{- | Pricing BC の Cost VO 単体テスト (US21, IT6)

輸送料金は「金額 (最小通貨単位の Integer) + 通貨 (ISO 4217 3 文字)」の組で表現する。
data-model.md §設計判断 3 (BIGINT + VARCHAR(3)) に整合。

観点:
- ISO 4217 の 3 文字通貨コードを受理
- 通貨コード長・アルファベット以外は不正
- 金額 0 は許容 (無料料金 or 割引満額)
- 負の金額は不正
- 同通貨同士は加算・減算可能
- 異通貨の演算は CurrencyMismatch
-}
module Pricing.Domain.Model.Value.CostSpec (spec) where

import Test.Hspec

import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency (..),
    addCost,
    mkCost,
    mkCurrency,
    subCost,
    zeroCost,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

jpy :: Currency
jpy = case mkCurrency "JPY" of
  Right c -> c
  Left _ -> error "JPY should be valid"

usd :: Currency
usd = case mkCurrency "USD" of
  Right c -> c
  Left _ -> error "USD should be valid"

spec :: Spec
spec = do
  describe "mkCurrency (US21)" $ do
    it "ISO 4217 の 3 文字大文字コードを受理する (JPY / USD / EUR)" $ do
      mkCurrency "JPY" `shouldBe` Right (Currency "JPY")
      mkCurrency "USD" `shouldBe` Right (Currency "USD")
      mkCurrency "EUR" `shouldBe` Right (Currency "EUR")

    it "2 文字は InvalidCurrency" $
      mkCurrency "JP" `shouldBe` Left (InvalidCurrency "JP")

    it "4 文字は InvalidCurrency" $
      mkCurrency "JPYX" `shouldBe` Left (InvalidCurrency "JPYX")

    it "小文字は InvalidCurrency (規約上大文字のみ)" $
      mkCurrency "jpy" `shouldBe` Left (InvalidCurrency "jpy")

    it "空文字は InvalidCurrency" $
      mkCurrency "" `shouldBe` Left (InvalidCurrency "")

  describe "mkCost (US21)" $ do
    it "0 金額を受理する (無料 or 満額割引の表現)" $ do
      mkCost 0 jpy `shouldSatisfy` \case
        Right c -> costAmount c == 0 && costCurrency c == jpy
        _ -> False

    it "正の金額を受理する" $
      mkCost 12000 jpy `shouldSatisfy` \case
        Right c -> costAmount c == 12000
        _ -> False

    it "負の金額は InvalidCost" $
      mkCost (-1) jpy `shouldBe` Left (InvalidCost (-1))

    it "非常に大きな Integer 金額も受理 (最小通貨単位のため)" $
      mkCost 99999999999999 jpy `shouldSatisfy` \case
        Right c -> costAmount c == 99999999999999
        _ -> False

  describe "zeroCost" $ do
    it "任意の通貨で 0 円/0 セント Cost を作れる" $ do
      costAmount (zeroCost jpy) `shouldBe` 0
      costCurrency (zeroCost jpy) `shouldBe` jpy

  describe "addCost (US21 加算)" $ do
    it "同通貨同士は Right で合計を返す" $ do
      let a = case mkCost 1000 jpy of Right x -> x; _ -> error "unreachable"
          b = case mkCost 2500 jpy of Right x -> x; _ -> error "unreachable"
      addCost a b `shouldSatisfy` \case
        Right c -> costAmount c == 3500 && costCurrency c == jpy
        _ -> False

    it "異通貨は CurrencyMismatch" $ do
      let a = case mkCost 1000 jpy of Right x -> x; _ -> error "unreachable"
          b = case mkCost 10 usd of Right x -> x; _ -> error "unreachable"
      addCost a b `shouldBe` Left (CurrencyMismatch "JPY" "USD")

  describe "subCost (US21 減算、割引適用等)" $ do
    it "同通貨で被減数 >= 減数なら Right" $ do
      let a = case mkCost 5000 jpy of Right x -> x; _ -> error "unreachable"
          b = case mkCost 3000 jpy of Right x -> x; _ -> error "unreachable"
      subCost a b `shouldSatisfy` \case
        Right c -> costAmount c == 2000
        _ -> False

    it "同通貨で被減数 < 減数なら InvalidCost (負値回避)" $ do
      let a = case mkCost 1000 jpy of Right x -> x; _ -> error "unreachable"
          b = case mkCost 3000 jpy of Right x -> x; _ -> error "unreachable"
      subCost a b `shouldBe` Left (InvalidCost (-2000))

    it "異通貨は CurrencyMismatch" $ do
      let a = case mkCost 5000 jpy of Right x -> x; _ -> error "unreachable"
          b = case mkCost 10 usd of Right x -> x; _ -> error "unreachable"
      subCost a b `shouldBe` Left (CurrencyMismatch "JPY" "USD")
