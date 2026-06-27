{-# LANGUAGE OverloadedStrings #-}

{- | ログイン API のテスト (IT1 AUTH 1.4)

Servant の login ハンドラを hspec-wai で実行し、HTTP レベルの振る舞いを検証する:
- 200 (JSON で JWT を返す)
- 401 (資格情報不一致)
- 422 (リクエストボディが壊れている)

IT2 で Cookie ベース (HttpOnly) のセッションへ拡張予定だが、IT1 では
まず JSON で JWT を返すシンプルな API として実装する。
-}
module Shared.Auth.Interfaces.LoginApiSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import Network.HTTP.Types (methodPost)
import Network.Wai (Application)
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier (..),
    UserRepository (..),
  )
import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    PasswordHash (..),
    Role (..),
    User (..),
    UserId (..),
  )
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( Claims (..),
    JwtSecret (..),
    JwtTtlSeconds (..),
    verifyAndDecode,
  )
import Cargotracker.Shared.Auth.Interfaces.LoginApi (LoginResponse (..), loginApp)
import Data.Aeson (decode)
import Data.Time.Clock.POSIX (POSIXTime)

dummyUser :: User
dummyUser =
  User
    { userId = UserId "alice"
    , userEmail = Email "alice@example.com"
    , userPasswordHash =
        PasswordHash
          "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123"
    , userRole = Sales
    }

fakeRepo :: UserRepository IO
fakeRepo =
  UserRepository
    { findByEmail = \e ->
        pure $
          if e == Email "alice@example.com"
            then Just dummyUser
            else Nothing
    }

-- 「valid-pass + dummyUser.hash」 のみ True
fakeVerifier :: PasswordVerifier IO
fakeVerifier =
  PasswordVerifier
    { verify = \plain _ -> pure (plain == "valid-pass")
    }

testApp :: IO Application
testApp = pure (loginApp fakeRepo fakeVerifier testSecret testTtl fixedNow)

testSecret :: JwtSecret
testSecret = JwtSecret "test-secret-32-bytes-min-for-jwt-hs256"

testTtl :: JwtTtlSeconds
testTtl = JwtTtlSeconds 3600

-- 固定 POSIX 時刻 (2026-06-27T00:00:00Z = 1782950400) を供給
fixedNowEpoch :: Integer
fixedNowEpoch = 1782950400

fixedNow :: IO POSIXTime
fixedNow = pure (realToFrac fixedNowEpoch)

spec :: Spec
spec = with testApp $ do
  describe "POST /login" $ do
    it "正しい資格情報なら 200 と JWT を返す" $ do
      let body = "{\"email\":\"alice@example.com\",\"password\":\"valid-pass\"}"
      request methodPost "/login" [("Content-Type", "application/json")] body
        `shouldRespondWith` 200

    it "資格情報不一致は 401" $ do
      let body = "{\"email\":\"alice@example.com\",\"password\":\"wrong-pass\"}"
      request methodPost "/login" [("Content-Type", "application/json")] body
        `shouldRespondWith` 401

    it "存在しないメールも 401 (存在判定を漏らさない)" $ do
      let body = "{\"email\":\"ghost@example.com\",\"password\":\"any\"}"
      request methodPost "/login" [("Content-Type", "application/json")] body
        `shouldRespondWith` 401

    it "壊れた JSON は 400" $ do
      let body = LBC.pack "not-a-json"
      request methodPost "/login" [("Content-Type", "application/json")] body
        `shouldRespondWith` 400

    it "T-02: 発行された JWT の exp は now+ttl で、固定値 9999999999 ではない" $ do
      let body = "{\"email\":\"alice@example.com\",\"password\":\"valid-pass\"}"
      res <-
        request methodPost "/login" [("Content-Type", "application/json")] body
      let bodyBytes = simpleBody res
      case decode bodyBytes :: Maybe LoginResponse of
        Nothing ->
          liftIO (expectationFailure "expected LoginResponse JSON")
        Just lr ->
          case verifyAndDecode testSecret (token lr) of
            Left err ->
              liftIO (expectationFailure ("JWT decode failed: " <> show err))
            Right c -> liftIO $ do
              claimsExp c `shouldBe` fixedNowEpoch + unJwtTtlSeconds testTtl
              claimsExp c `shouldNotBe` 9999999999
