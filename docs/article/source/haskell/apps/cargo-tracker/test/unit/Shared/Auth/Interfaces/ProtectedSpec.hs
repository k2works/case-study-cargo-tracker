{-# LANGUAGE OverloadedStrings #-}

{- | RBAC 保護エンドポイントのテスト (IT1 AUTH 1.5)

Authorization: Bearer <jwt> ヘッダから JWT を取り出し、claims.role が
許可リストに含まれていれば inner handler を実行する。
- ヘッダなし → 401
- 不正な JWT → 401
- 期限切れ → 401
- ロール不一致 → 403
- 許可ロール → 200

型レベル `RequireRole '[..]` は IT2 で導入予定。IT1 では関数レベルで
始める (Application 層の Authorization Policy ポートとして抽象化)。
-}
module Shared.Auth.Interfaces.ProtectedSpec (spec) where

import qualified Data.ByteString.Char8 as BC
import qualified Data.Text as T
import Data.Text.Encoding (encodeUtf8)
import Network.HTTP.Types (methodGet)
import Network.Wai (Application)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    Role (..),
    UserId (..),
  )
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( Claims (..),
    JwtSecret (..),
    issue,
  )
import Cargotracker.Shared.Auth.Interfaces.Protected
  ( protectedApp,
  )

secret :: JwtSecret
secret = JwtSecret "test-secret-32-bytes-min-for-jwt-hs256"

claimsForRole :: Role -> Claims
claimsForRole r =
  Claims
    { claimsUserId = UserId "alice"
    , claimsEmail = Email "alice@example.com"
    , claimsRole = r
    , claimsExp = 9999999999
    }

mkBearer :: T.Text -> BC.ByteString
mkBearer tok = "Bearer " <> encodeUtf8 tok

-- Sales / MasterAdmin のみアクセス可な保護エンドポイント
testApp :: IO Application
testApp = pure (protectedApp secret [Sales, MasterAdmin])

spec :: Spec
spec = with testApp $ do
  describe "GET /protected (RBAC: [Sales, MasterAdmin])" $ do
    it "Authorization なしは 401" $
      get "/protected" `shouldRespondWith` 401

    it "壊れた Bearer トークンは 401" $
      request methodGet "/protected" [("Authorization", "Bearer broken.jwt.value")] ""
        `shouldRespondWith` 401

    it "Bearer の prefix がないと 401" $ do
      tok <- liftIO (issue secret (claimsForRole Sales))
      request methodGet "/protected" [("Authorization", encodeUtf8 tok)] ""
        `shouldRespondWith` 401

    it "許可外ロール (Tracker) は 403" $ do
      tok <- liftIO (issue secret (claimsForRole Tracker))
      request methodGet "/protected" [("Authorization", mkBearer tok)] ""
        `shouldRespondWith` 403

    it "許可ロール (Sales) は 200" $ do
      tok <- liftIO (issue secret (claimsForRole Sales))
      request methodGet "/protected" [("Authorization", mkBearer tok)] ""
        `shouldRespondWith` 200

    it "許可ロール (MasterAdmin) も 200" $ do
      tok <- liftIO (issue secret (claimsForRole MasterAdmin))
      request methodGet "/protected" [("Authorization", mkBearer tok)] ""
        `shouldRespondWith` 200
  where
    liftIO = Test.Hspec.Wai.liftIO
