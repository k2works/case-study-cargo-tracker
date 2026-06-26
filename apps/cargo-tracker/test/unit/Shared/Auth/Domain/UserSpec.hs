{- | AUTH ドメイン値オブジェクトのテスト (IT1 AUTH 1.1)
ADR 0002: スマートコンストラクタで不正値を構築不能にする。
-}
module Shared.Auth.Domain.UserSpec (spec) where

import Test.Hspec

import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    PasswordHash (..),
    Role (..),
    User (..),
    UserId (..),
    mkEmail,
    mkPasswordHash,
    mkUserId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

spec :: Spec
spec = do
  describe "UserId" $ do
    it "空文字列は不正" $
      mkUserId "" `shouldBe` Left (InvalidUserId "empty")
    it "有効な ID は構築できる" $
      mkUserId "alice" `shouldBe` Right (UserId "alice")

  describe "Email" $ do
    it "@ を含まない文字列は不正" $
      mkEmail "no-at-sign" `shouldBe` Left (InvalidEmail "no @ symbol")
    it "ローカル部または ドメイン部が空は不正" $
      mkEmail "@example.com" `shouldBe` Left (InvalidEmail "empty local part")
    it "有効なメールアドレスは構築できる" $
      mkEmail "alice@example.com" `shouldBe` Right (Email "alice@example.com")

  describe "PasswordHash" $ do
    it "60 文字未満の bcrypt ハッシュは不正" $
      mkPasswordHash "too-short" `shouldBe` Left (InvalidPasswordHash "expected 60 chars")
    it "60 文字の bcrypt 風ハッシュは構築できる" $ do
      let h = "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123"
      mkPasswordHash h `shouldBe` Right (PasswordHash h)

  describe "Role" $ do
    it "8 種の役割が定義されている" $ do
      let roles =
            [ Shipper
            , Consignee
            , Sales
            , Router
            , Tracker
            , Handler
            , Accountant
            , MasterAdmin
            ]
      length roles `shouldBe` 8

  describe "User 集約" $ do
    it "識別子 / メール / ハッシュ / 役割でフィールド構築できる" $ do
      let Right uid = mkUserId "alice"
          Right email = mkEmail "alice@example.com"
          Right hash = mkPasswordHash "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123"
          user = User uid email hash Sales
      userId user `shouldBe` uid
      userRole user `shouldBe` Sales
