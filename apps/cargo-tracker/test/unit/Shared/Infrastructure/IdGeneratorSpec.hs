{-# LANGUAGE OverloadedStrings #-}

{- | IdGenerator のテスト (H-02)

partial 関数 `alphaNumTable !! i` を total 関数 `intToAlphaNumChar` に置換した
ことを保証する。境界値 (0, 9, 10, 35) と 36 通り全網羅を hedgehog でテスト。
-}
module Shared.Infrastructure.IdGeneratorSpec (spec) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.Text as T
import Hedgehog (Property, assert, check, forAll, property)
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Shared.Infrastructure.IdGenerator
  ( generateBookingIdText,
    generateShipperIdText,
    intToAlphaNumChar,
  )

prop_intToAlphaNumChar_in_range :: Property
prop_intToAlphaNumChar_in_range = property $ do
  i <- forAll (Gen.int (Range.constant 0 35))
  let c = intToAlphaNumChar i
  assert (c `elem` (['0' .. '9'] <> ['A' .. 'Z']))

runProp :: String -> Property -> Spec
runProp name p = it name $ do
  ok <- liftIO (check p)
  ok `shouldBe` True

spec :: Spec
spec = do
  describe "intToAlphaNumChar (H-02 total 化)" $ do
    it "0 → '0'" $ intToAlphaNumChar 0 `shouldBe` '0'
    it "9 → '9'" $ intToAlphaNumChar 9 `shouldBe` '9'
    it "10 → 'A'" $ intToAlphaNumChar 10 `shouldBe` 'A'
    it "35 → 'Z'" $ intToAlphaNumChar 35 `shouldBe` 'Z'
    it "範囲外 (負値) は '0' に丸める (到達不能だが total 保証)" $
      intToAlphaNumChar (-1) `shouldBe` '0'
    it "範囲外 (36 以上) は '0' に丸める (到達不能だが total 保証)" $
      intToAlphaNumChar 36 `shouldBe` '0'
    runProp "36 通りすべてが英数大文字に写る (プロパティ)" prop_intToAlphaNumChar_in_range

  describe "generateBookingIdText" $ do
    it "BK- プレフィックス + 6 文字英数大文字を返す" $ do
      t <- generateBookingIdText
      T.length t `shouldBe` 9
      T.take 3 t `shouldBe` "BK-"
      T.all (`elem` ['0' .. '9'] <> ['A' .. 'Z']) (T.drop 3 t) `shouldBe` True

  describe "generateShipperIdText" $ do
    it "SHP- プレフィックス + 6 文字英数大文字を返す" $ do
      t <- generateShipperIdText
      T.length t `shouldBe` 10
      T.take 4 t `shouldBe` "SHP-"
      T.all (`elem` ['0' .. '9'] <> ['A' .. 'Z']) (T.drop 4 t) `shouldBe` True
