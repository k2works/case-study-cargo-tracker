{-# LANGUAGE OverloadedStrings #-}

{- | PostgresUserRepository の統合テスト (IT1)

実 PostgreSQL に接続して動作確認する。`DATABASE_URL` 環境変数があれば
実行、なければ pendingWith でスキップ。

CI では testcontainers-hs に置き換える想定。IT1 段階では docker-compose
で起動した postgres を直接利用する。
-}
module Shared.Auth.Infrastructure.PostgresUserRepositorySpec (spec) where

import qualified Data.ByteString.Char8 as BC
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    close,
    connectPostgreSQL,
    execute,
  )
import System.Environment (lookupEnv)
import Test.Hspec

import Cargotracker.Shared.Auth.Application.Ports (UserRepository (..))
import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    Role (..),
    User (..),
    UserId (..),
  )
import Cargotracker.Shared.Auth.Infrastructure.PostgresUserRepository
  ( newPostgresUserRepository,
  )

withConn :: (Connection -> IO ()) -> IO ()
withConn action = do
  mUrl <- lookupEnv "DATABASE_URL"
  case mUrl of
    Nothing -> pendingWithMsg "DATABASE_URL not set; skipping integration tests"
    Just url -> do
      conn <- connectPostgreSQL (BC.pack url)
      action conn
      close conn
  where
    pendingWithMsg msg = expectationFailure ("PENDING: " <> msg)

-- 後始末用 (テスト前後で同じ email/user_id を作って消す)
cleanUser :: Connection -> IO ()
cleanUser conn = do
  _ <-
    execute
      conn
      "DELETE FROM users WHERE email = ?"
      (Only ("test-alice@example.com" :: String))
  pure ()

setupUser :: Connection -> IO ()
setupUser conn = do
  cleanUser conn
  _ <-
    execute
      conn
      "INSERT INTO users (user_id, email, password_hash) \
      \ VALUES (?, ?, ?)"
      ( "test-alice" :: String
      , "test-alice@example.com" :: String
      , "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123" :: String
      )
  _ <-
    execute
      conn
      "INSERT INTO user_roles (user_id, role) \
      \ SELECT id, 'Sales' FROM users WHERE email = ?"
      (Only ("test-alice@example.com" :: String))
  pure ()

spec :: Spec
spec = describe "PostgresUserRepository [INTEGRATION]" $ do
  it "DATABASE_URL があれば実 DB で findByEmail が動く" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing ->
        pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        setupUser conn
        let repo = newPostgresUserRepository conn
        result <- findByEmail repo (Email "test-alice@example.com")
        case result of
          Just u -> do
            userId u `shouldBe` UserId "test-alice"
            userRole u `shouldBe` Sales
          Nothing -> expectationFailure "expected Just user, got Nothing"
        cleanUser conn
        close conn

  it "存在しないメールは Nothing" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing ->
        pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        let repo = newPostgresUserRepository conn
        result <- findByEmail repo (Email "ghost@example.com")
        result `shouldBe` Nothing
        close conn
