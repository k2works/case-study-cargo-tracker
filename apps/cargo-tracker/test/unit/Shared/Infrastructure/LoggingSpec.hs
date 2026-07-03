-- | Cargotracker.Shared.Infrastructure.Logging のテスト (T6-07, IT7)
module Shared.Infrastructure.LoggingSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Infrastructure.Logging (newCorrelationId)

spec :: Spec
spec = describe "Cargotracker.Shared.Infrastructure.Logging" $ do
  describe "newCorrelationId (T6-07 UUID v4)" $ do
    it "36 文字の UUID 形式を返す (8-4-4-4-12)" $ do
      cid <- newCorrelationId
      T.length cid `shouldBe` 36
      T.count "-" cid `shouldBe` 4

    it "呼出ごとに異なる ID を生成する (衝突しない)" $ do
      c1 <- newCorrelationId
      c2 <- newCorrelationId
      c1 `shouldNotBe` c2
