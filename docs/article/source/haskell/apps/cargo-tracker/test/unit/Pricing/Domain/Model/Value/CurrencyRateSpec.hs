{-# LANGUAGE OverloadedStrings #-}

{- | CurrencyRate エンティティの単体テスト (US21, IT6)

通貨換算レートを保持するエンティティ。有効期限 (validFrom / validTo) を持ち、
現在時刻に有効なレートで Cost を別通貨に変換する `convert` 関数を提供する。

レートは「1 単位の from 通貨 = amount 単位の to 通貨」を精度保持のため
分子・分母の Integer ペアで表現 (data-model.md §設計判断 3 に整合、
NUMERIC(18,8) を Integer * 10^8 として扱う想定)。

観点:
- 有効期間内に convert すると換算後の Cost を返す
- 期限切れ / 開始前は CurrencyRateExpired
- from 通貨が Cost.costCurrency と一致しないと CurrencyMismatch
-}
module Pricing.Domain.Model.Value.CurrencyRateSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    Currency (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate
  ( CurrencyRate (..),
    convert,
    isRateValidAt,
    mkCurrencyRate,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

jpy :: Currency
jpy = Currency "JPY"

usd :: Currency
usd = Currency "USD"

-- 2026-07-01 00:00 〜 2026-08-01 00:00 に有効な 1 USD = 150 JPY
sampleRate :: CurrencyRate
sampleRate = case mkCurrencyRate usd jpy 150 t0 t1 of
  Right r -> r
  Left _ -> error "unreachable"
  where
    t0 = UTCTime (fromGregorian 2026 7 1) (secondsToDiffTime 0)
    t1 = UTCTime (fromGregorian 2026 8 1) (secondsToDiffTime 0)

atMid :: UTCTime
atMid = UTCTime (fromGregorian 2026 7 15) (secondsToDiffTime 0)

beforeStart :: UTCTime
beforeStart = UTCTime (fromGregorian 2026 6 30) (secondsToDiffTime 0)

afterEnd :: UTCTime
afterEnd = UTCTime (fromGregorian 2026 8 2) (secondsToDiffTime 0)

spec :: Spec
spec = do
  describe "mkCurrencyRate (US21)" $ do
    it "正常系: fromCurrency / toCurrency / rate / validFrom / validTo を受理" $
      mkCurrencyRate usd jpy 150 (UTCTime (fromGregorian 2026 1 1) 0) (UTCTime (fromGregorian 2027 1 1) 0)
        `shouldSatisfy` \case
          Right r -> crRate r == 150
          _ -> False

    it "同一通貨は InvalidCurrency" $
      mkCurrencyRate jpy jpy 100 (UTCTime (fromGregorian 2026 1 1) 0) (UTCTime (fromGregorian 2027 1 1) 0)
        `shouldBe` Left (InvalidCurrency "JPY")

    it "負のレートは InvalidCost" $
      mkCurrencyRate usd jpy (-1) (UTCTime (fromGregorian 2026 1 1) 0) (UTCTime (fromGregorian 2027 1 1) 0)
        `shouldBe` Left (InvalidCost (-1))

    it "validFrom >= validTo は InvalidCurrencyRatePeriod" $
      mkCurrencyRate usd jpy 100 (UTCTime (fromGregorian 2026 7 1) 0) (UTCTime (fromGregorian 2026 7 1) 0)
        `shouldBe` Left InvalidCurrencyRatePeriod

  describe "isRateValidAt (US21 有効期限判定)" $ do
    it "期間内は True" $
      isRateValidAt atMid sampleRate `shouldBe` True

    it "期間開始前は False" $
      isRateValidAt beforeStart sampleRate `shouldBe` False

    it "期間終了後は False" $
      isRateValidAt afterEnd sampleRate `shouldBe` False

    it "境界: validFrom ちょうどは True (>= で扱う)" $
      isRateValidAt (UTCTime (fromGregorian 2026 7 1) 0) sampleRate `shouldBe` True

    it "境界: validTo ちょうどは False (< で扱う)" $
      isRateValidAt (UTCTime (fromGregorian 2026 8 1) 0) sampleRate `shouldBe` False

  describe "convert (US21 通貨換算)" $ do
    it "期間内で fromCurrency 一致なら換算後 Cost を返す (10 USD → 1500 JPY)" $
      convert sampleRate atMid (Cost 10 usd) `shouldBe` Right (Cost 1500 jpy)

    it "期限切れは CurrencyRateExpired" $
      convert sampleRate afterEnd (Cost 10 usd) `shouldBe` Left CurrencyRateExpired

    it "開始前は CurrencyRateExpired" $
      convert sampleRate beforeStart (Cost 10 usd) `shouldBe` Left CurrencyRateExpired

    it "Cost の通貨が from と異なる場合は CurrencyMismatch" $
      convert sampleRate atMid (Cost 10 jpy)
        `shouldBe` Left (CurrencyMismatch "JPY" "USD")
