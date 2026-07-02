{-# LANGUAGE OverloadedStrings #-}

{- | 汎用 bcrypt ハッシュヘルパのテスト (T5-02 Phase 2, SEC-04, IT6)

ConfirmationCode / API キーなどのシークレットを bcrypt で保存・検証するための
`hashSecret` / `verifySecret` を検証する。BcryptVerifier (Auth 専用) と同じ
`Crypto.BCrypt` を使うが、Domain 非依存の Text 入出力に統一している。
-}
module Shared.Security.BcryptHashSpec (spec) where

import Test.Hspec

import Cargotracker.Shared.Security.BcryptHash (hashSecret, verifySecret)

spec :: Spec
spec = describe "hashSecret / verifySecret (T5-02 Phase 2)" $ do
  it "hashSecret は毎回異なるハッシュを返す (bcrypt salt がランダムなため)" $ do
    h1 <- hashSecret "123456"
    h2 <- hashSecret "123456"
    h1 `shouldNotBe` h2

  it "verifySecret は同じ平文に対して True" $ do
    h <- hashSecret "123456"
    verifySecret "123456" h `shouldBe` True

  it "verifySecret は異なる平文に対して False" $ do
    h <- hashSecret "123456"
    verifySecret "123457" h `shouldBe` False

  it "verifySecret はハッシュ形式が壊れていても False (例外を投げない)" $
    verifySecret "123456" "not-a-bcrypt-hash" `shouldBe` False

  it "verifySecret は空平文と正当ハッシュの組合せで False" $ do
    h <- hashSecret "123456"
    verifySecret "" h `shouldBe` False

  it "非 ASCII (日本語) を含むシークレットもハッシュ化・検証できる" $ do
    h <- hashSecret "秘密パスワード"
    verifySecret "秘密パスワード" h `shouldBe` True
    verifySecret "秘密ぱすわーど" h `shouldBe` False
