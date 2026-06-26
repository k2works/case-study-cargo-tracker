{- | bcrypt パスワード検証実装のテスト (IT1 AUTH 1.3 一部)

bcrypt はソルト付き不可逆ハッシュなので、同じ平文に対しても異なる
ハッシュが生成される。検証は `Crypto.BCrypt.validatePassword` を使う。

実 bcrypt ライブラリを呼び出すので統合テストの性格を持つが、
ネットワーク・DB を使わないため unit テストに置く。
-}
module Shared.Auth.Infrastructure.BcryptVerifierSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Auth.Application.Ports (PasswordVerifier (..))
import Cargotracker.Shared.Auth.Domain.User (PasswordHash (..))
import Cargotracker.Shared.Auth.Infrastructure.BcryptVerifier
  ( hashPassword,
    newBcryptVerifier,
  )

spec :: Spec
spec = do
  describe "hashPassword" $ do
    it "生成したハッシュは 60 文字" $ do
      hash <- hashPassword "secret-password"
      T.length (unPasswordHash hash) `shouldBe` 60
    it "ハッシュは bcrypt フォーマット ($2 で始まる) " $ do
      hash <- hashPassword "any-password"
      T.take 2 (unPasswordHash hash) `shouldBe` "$2"
    it "同じ平文でも異なるハッシュが返る (ソルトが違うため)" $ do
      a <- hashPassword "same-pass"
      b <- hashPassword "same-pass"
      a `shouldNotBe` b

  describe "newBcryptVerifier" $ do
    it "正しいパスワードは True を返す" $ do
      hash <- hashPassword "valid-pass"
      let v = newBcryptVerifier
      ok <- verify v "valid-pass" hash
      ok `shouldBe` True
    it "誤ったパスワードは False を返す" $ do
      hash <- hashPassword "valid-pass"
      let v = newBcryptVerifier
      ok <- verify v "wrong-pass" hash
      ok `shouldBe` False
    it "空文字パスワードは False を返す" $ do
      hash <- hashPassword "non-empty"
      let v = newBcryptVerifier
      ok <- verify v "" hash
      ok `shouldBe` False
