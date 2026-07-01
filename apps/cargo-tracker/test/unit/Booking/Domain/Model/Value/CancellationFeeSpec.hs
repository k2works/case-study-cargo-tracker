{- | CancellationFee VO 単体テスト (US13, IT5 task 3.7 / T4-10)

IT4 code review M-07 で「CancellationPolicy の単体テストはあるが、CancellationFee VO 自体の
テストがない (tierRate 3 値 + Enum/Bounded 境界)」と指摘された。IT5 で本 spec を追加し、
domain-model.md で定義された料率 (0% / 30% / 100%) と Enum/Bounded 網羅性をテストする。
-}
module Booking.Domain.Model.Value.CancellationFeeSpec (spec) where

import Data.Ratio ((%))
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationTier (..),
    tierRate,
  )

spec :: Spec
spec = describe "CancellationFee VO (US13, IT5 T4-10)" $ do
  describe "tierRate" $ do
    it "Free は 0% (0 % 100)" $
      tierRate Free `shouldBe` 0 % 100

    it "Partial は 30% (30 % 100)" $
      tierRate Partial `shouldBe` 30 % 100

    it "Full は 100% (100 % 100)" $
      tierRate Full `shouldBe` 100 % 100

    it "Free < Partial < Full の順で料率が単調増加" $ do
      tierRate Free `shouldSatisfy` (< tierRate Partial)
      tierRate Partial `shouldSatisfy` (< tierRate Full)

  describe "Enum / Bounded 網羅性" $ do
    it "minBound == Free" $
      (minBound :: CancellationTier) `shouldBe` Free

    it "maxBound == Full" $
      (maxBound :: CancellationTier) `shouldBe` Full

    it "全 3 値 [Free, Partial, Full] が enumFromTo で列挙できる" $
      [minBound .. maxBound :: CancellationTier] `shouldBe` [Free, Partial, Full]
