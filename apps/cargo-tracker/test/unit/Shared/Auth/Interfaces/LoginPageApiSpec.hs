{-# LANGUAGE OverloadedStrings #-}

-- | GET /login HTML ページの hspec-wai テスト
module Shared.Auth.Interfaces.LoginPageApiSpec (spec) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchBody (..))

import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier (..),
    UserRepository (..),
  )
import Cargotracker.Shared.Auth.Application.SessionPorts
  ( SessionRepository (..),
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

spec :: Spec
spec = with (pure (loginPageApp fakeRepo fakeVerifier fakeSessionRepo)) $ do
  describe "GET /login" $ do
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
