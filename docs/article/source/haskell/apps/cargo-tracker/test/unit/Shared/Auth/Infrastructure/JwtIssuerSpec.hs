{- | JWT 発行・検証実装のテスト (IT1 AUTH 1.3 一部)

HS256 (HMAC-SHA256) アルゴリズムによる JWT 発行と検証を行う。
servant-auth-server を後追いで導入する代わりに、まず最小限の自作実装で
ポート契約を満たし、IT1 末で必要なら servant-auth-server に差し替える。
-}
module Shared.Auth.Infrastructure.JwtIssuerSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    Role (..),
    UserId (..),
  )
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( Claims (..),
    JwtSecret (..),
    issue,
    verifyAndDecode,
  )

sampleSecret :: JwtSecret
sampleSecret = JwtSecret "test-secret-key-do-not-use-in-prod-32chars-min"

sampleClaims :: Claims
sampleClaims =
  Claims
    { claimsUserId = UserId "alice"
    , claimsEmail = Email "alice@example.com"
    , claimsRole = Sales
    , claimsExp = 9999999999 -- 西暦 2286 年なので IT1 のテスト中に切れない
    }

spec :: Spec
spec = do
  describe "issue" $ do
    it "生成された JWT は 3 つのドット区切りパートで構成される" $ do
      tok <- issue sampleSecret sampleClaims
      length (T.splitOn "." tok) `shouldBe` 3
    it "同じ claims + secret なら同じトークン (HS256 は決定的)" $ do
      t1 <- issue sampleSecret sampleClaims
      t2 <- issue sampleSecret sampleClaims
      t1 `shouldBe` t2
    it "ヘッダ部は HS256 を示す" $ do
      tok <- issue sampleSecret sampleClaims
      let header = head (T.splitOn "." tok)
      -- base64url で {"alg":"HS256","typ":"JWT"} を期待
      header
        `shouldBe` "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"

  describe "verifyAndDecode" $ do
    it "正しい署名と secret なら Claims を復号" $ do
      tok <- issue sampleSecret sampleClaims
      verifyAndDecode sampleSecret tok `shouldBe` Right sampleClaims
    it "改ざんされた payload は Left 失敗" $ do
      tok <- issue sampleSecret sampleClaims
      let parts = T.splitOn "." tok
          tampered = T.intercalate "." [head parts, "TAMPERED", parts !! 2]
      case verifyAndDecode sampleSecret tampered of
        Left _ -> pure ()
        Right _ -> expectationFailure "改ざんで成功してはいけない"
    it "異なる secret で検証は Left" $ do
      tok <- issue sampleSecret sampleClaims
      let wrongSecret = JwtSecret "another-secret-key-completely-different-32"
      case verifyAndDecode wrongSecret tok of
        Left _ -> pure ()
        Right _ -> expectationFailure "違う secret で検証成功してはいけない"
