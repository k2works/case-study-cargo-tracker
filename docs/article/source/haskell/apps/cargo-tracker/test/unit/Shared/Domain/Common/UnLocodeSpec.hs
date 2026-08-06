{- | UN/LOCODE 値オブジェクトのテスト (IT1 共有カーネル)

UN/LOCODE: 国際連合 LOCATION CODE。5 文字 (国コード 2 + 場所コード 3)。
例: JPTYO (東京), USNYC (ニューヨーク)。
-}
module Shared.Domain.Common.UnLocodeSpec (spec) where

import Test.Hspec

import Cargotracker.Shared.Domain.Common.UnLocode
  ( UnLocode (..),
    mkUnLocode,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

spec :: Spec
spec = do
  describe "mkUnLocode" $ do
    it "5 文字でないと不正" $
      mkUnLocode "JPT" `shouldBe` Left (InvalidUnLocode "expected 5 chars")
    it "国コード部 (1-2 文字目) が大文字英字でないと不正" $
      mkUnLocode "jPTYO" `shouldBe` Left (InvalidUnLocode "country code must be 2 uppercase letters")
    it "場所コード部 (3-5 文字目) が英数字でないと不正" $
      mkUnLocode "JP@YO" `shouldBe` Left (InvalidUnLocode "location code must be alphanumeric")
    it "有効な UN/LOCODE は構築できる" $ do
      mkUnLocode "JPTYO" `shouldBe` Right (UnLocode "JPTYO")
      mkUnLocode "USNYC" `shouldBe` Right (UnLocode "USNYC")
