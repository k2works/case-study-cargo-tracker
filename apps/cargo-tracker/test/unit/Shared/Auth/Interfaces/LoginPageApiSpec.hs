{-# LANGUAGE OverloadedStrings #-}

-- | GET /login HTML ページと POST /login (Session Cookie 発行) の hspec-wai テスト
module Shared.Auth.Interfaces.LoginPageApiSpec (spec) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import qualified Data.Text as T
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchBody (..), MatchHeader (..))

import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier (..),
    UserRepository (..),
  )
import Cargotracker.Shared.Auth.Application.SessionPorts
  ( SessionRepository (..),
  )
import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    PasswordHash (..),
    Role (..),
    User (..),
    UserId (..),
  )
import Cargotracker.Shared.Auth.Interfaces.LoginPageApi (loginPageApp)

fakeRepo :: UserRepository IO
fakeRepo = UserRepository {findByEmail = \_ -> pure Nothing}

fakeVerifier :: PasswordVerifier IO
fakeVerifier = PasswordVerifier {verify = \_ _ -> pure False}

-- IT5 task 1.2: セッション Cookie 発行用の SessionRepository スタブ
fakeSessionRepo :: SessionRepository IO
fakeSessionRepo =
  SessionRepository
    { saveSession = \_ -> pure (Right ())
    , findByToken = \_ -> pure Nothing
    , deleteByToken = \_ -> pure (Right ())
    , touchLastUsed = \_ -> pure (Right ())
    }

bodyContains :: BS.ByteString -> MatchBody
bodyContains needle = MatchBody $ \_ body ->
  if needle `isInfixOfBS` LBS.toStrict body
    then Nothing
    else Just ("body does not contain: " <> show needle)

isInfixOfBS :: BS.ByteString -> BS.ByteString -> Bool
isInfixOfBS needle hay = BS.length needle == 0 || any (BS.isPrefixOf needle) (BS.tails hay)

--------------------------------------------------------------------------------
-- POST /login (Session Cookie 発行) 用フィクスチャ (T5-10, IT6)
--------------------------------------------------------------------------------

authorizedUser :: User
authorizedUser =
  User
    { userId = UserId "user-001"
    , userEmail = Email "sales@example.com"
    , userPasswordHash = PasswordHash "hashed-doesnt-matter-here"
    , userRole = Sales
    }

-- LoginCommand.execute が成功するように、指定 email のユーザーを返す UserRepo。
userRepoOf :: User -> UserRepository IO
userRepoOf u =
  UserRepository
    { findByEmail = \e -> pure (if e == userEmail u then Just u else Nothing)
    }

-- 特定の平文パスワードだけを受理する PasswordVerifier。
verifierAcceptingOnly :: T.Text -> PasswordVerifier IO
verifierAcceptingOnly accepted =
  PasswordVerifier
    { verify = \plain _ -> pure (plain == accepted)
    }

-- Set-Cookie ヘッダの値に部分文字列を含むかを検査する MatchHeader。
setCookieContains :: BS.ByteString -> MatchHeader
setCookieContains needle = MatchHeader $ \hs _ ->
  let cookies = [v | (n, v) <- hs, n == "Set-Cookie"]
   in if any (needle `isInfixOfBS`) cookies
        then Nothing
        else
          Just
            ( "no Set-Cookie header contains "
                <> show needle
                <> "; got: "
                <> show cookies
            )

--------------------------------------------------------------------------------
-- spec
--------------------------------------------------------------------------------

spec :: Spec
spec = do
  describe "Cargotracker.Shared.Auth.Interfaces.LoginPageApi" $ do
    describe "GET /login (静的ページ)" $
      with (pure (loginPageApp fakeRepo fakeVerifier fakeSessionRepo)) $ do
        it "200 を返す" $
          get "/login" `shouldRespondWith` 200

        it "本文に Cargo Tracker が含まれる" $
          get "/login"
            `shouldRespondWith` 200 {matchBody = bodyContains "Cargo Tracker"}

        it "本文に form action='/login' method='post' が含まれる" $
          get "/login"
            `shouldRespondWith` 200 {matchBody = bodyContains "action=\"/login\""}

        it "Bootstrap 5 が読み込まれている" $
          get "/login"
            `shouldRespondWith` 200 {matchBody = bodyContains "bootstrap@5"}

    describe "POST /login (T5-10: Session Cookie 発行)"
      $ with
        ( pure
            ( loginPageApp
                (userRepoOf authorizedUser)
                (verifierAcceptingOnly "correct-password")
                fakeSessionRepo
            )
        )
      $ do
        it "認証成功時: 303 See Other" $
          request
            "POST"
            "/login"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "email=sales@example.com&password=correct-password"
            `shouldRespondWith` 303

        it "認証成功時: Set-Cookie に cargo_session と HttpOnly / SameSite=Lax / Max-Age=28800 が含まれる" $
          request
            "POST"
            "/login"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "email=sales@example.com&password=correct-password"
            `shouldRespondWith` 303
              { matchHeaders =
                  [ setCookieContains "cargo_session="
                  , setCookieContains "HttpOnly"
                  , setCookieContains "SameSite=Lax"
                  , setCookieContains "Path=/"
                  , -- sessionTtlSeconds = 8 * 60 * 60 = 28800
                    setCookieContains "Max-Age=28800"
                  ]
              }

        it "認証失敗時 (パスワード誤): 401" $
          request
            "POST"
            "/login"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "email=sales@example.com&password=WRONG"
            `shouldRespondWith` 401

        it "認証失敗時 (存在しない email): 401" $
          request
            "POST"
            "/login"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "email=nobody@example.com&password=whatever"
            `shouldRespondWith` 401
