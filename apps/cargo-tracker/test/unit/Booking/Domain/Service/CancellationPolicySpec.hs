{- | CancellationPolicy ドメインサービスのテスト (US13, IT4)

3 段階ティアの境界値を網羅。
-}
module Booking.Domain.Service.CancellationPolicySpec (spec) where

import Data.Ratio ((%))
import Data.Time
  ( UTCTime (..),
    addUTCTime,
    fromGregorian,
    secondsToDiffTime,
  )
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
  )
import Cargotracker.Booking.Domain.Service.CancellationPolicy (calculate)

-- 出航日時の基準: 2026-09-10 12:00:00 UTC
departure :: UTCTime
departure = UTCTime (fromGregorian 2026 9 10) (secondsToDiffTime (12 * 3600))

-- 現在時刻を「出航 N 時間前」で表現するヘルパ
hoursBefore :: Double -> UTCTime
hoursBefore h = addUTCTime (realToFrac (negate (h * 3600))) departure

spec :: Spec
spec = describe "CancellationPolicy.calculate (US13 / IT4)" $ do
  describe "ティア境界値" $ do
    it "出航 7 日 (168h) 以上前なら Free / 0%" $ do
      let fee = calculate (hoursBefore 168) departure
      cfTier fee `shouldBe` Free
      cfRate fee `shouldBe` (0 % 100)

    it "出航 168h ちょうど (= 7 日) は Free" $ do
      let fee = calculate (hoursBefore 168.0) departure
      cfTier fee `shouldBe` Free

    it "出航 167h 前 (= 7 日未満かつ 1 日以上) は Partial / 30%" $ do
      let fee = calculate (hoursBefore 167) departure
      cfTier fee `shouldBe` Partial
      cfRate fee `shouldBe` (30 % 100)

    it "出航 24h ちょうど (= 1 日) は Partial" $ do
      let fee = calculate (hoursBefore 24.0) departure
      cfTier fee `shouldBe` Partial

    it "出航 23h 前 (= 24h 未満) は Full / 100%" $ do
      let fee = calculate (hoursBefore 23) departure
      cfTier fee `shouldBe` Full
      cfRate fee `shouldBe` (100 % 100)

    it "出航時刻と同時 (diff = 0) は Full" $ do
      let fee = calculate departure departure
      cfTier fee `shouldBe` Full

    it "出航後 (now > departure) は Full" $ do
      let fee = calculate (addUTCTime 3600 departure) departure
      cfTier fee `shouldBe` Full

  describe "cfCalculatedAt は引数の now をそのまま返す" $ do
    it "now と一致" $ do
      let now = hoursBefore 100
          fee = calculate now departure
      cfCalculatedAt fee `shouldBe` now
