{-# LANGUAGE OverloadedStrings #-}

-- | CustomsDeclaration / HsCode / DeclarationStatus のテスト (US27, IT3)
module Booking.Domain.Model.CustomsDeclarationSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Booking.Domain.Model.CustomsDeclaration
  ( CustomsDeclaration (..),
    mkCustomsDeclaration,
  )
import Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( DeclarationStatus (..),
    declarationStatusFromText,
    declarationStatusToText,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.HsCode
  ( mkHsCode,
    unHsCode,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

bid :: BookingId
bid = BookingId "BK-A1B2C3"

spec :: Spec
spec = do
  describe "HsCode" $ do
    it "6 桁の数字は Right" $
      fmap unHsCode (mkHsCode "123456") `shouldBe` Right "123456"
    it "10 桁の数字は Right" $
      fmap unHsCode (mkHsCode "0123456789") `shouldBe` Right "0123456789"
    it "5 桁は Left InvalidHsCode" $
      mkHsCode "12345" `shouldBe` Left (InvalidHsCode "12345")
    it "11 桁は Left InvalidHsCode" $
      mkHsCode "12345678901" `shouldBe` Left (InvalidHsCode "12345678901")
    it "英字混入は Left InvalidHsCode" $
      mkHsCode "12345A" `shouldBe` Left (InvalidHsCode "12345A")
    it "空文字は Left InvalidHsCode" $
      mkHsCode "" `shouldBe` Left (InvalidHsCode "")

  describe "DeclarationStatus" $ do
    it "全 4 状態が往復変換できる" $ do
      mapM_
        ( \s ->
            declarationStatusFromText (declarationStatusToText s) `shouldBe` Right s
        )
        [Pending, Cleared, Held, Rejected]
    it "未知の文字列は Left InvalidDeclarationStatus" $
      declarationStatusFromText "FOO" `shouldBe` Left (InvalidDeclarationStatus "FOO")
    it "小文字 pending は受理しない" $
      declarationStatusFromText "pending" `shouldBe` Left (InvalidDeclarationStatus "pending")

  describe "CustomsDeclaration" $ do
    it "正常系: HS コード + 通関業者名 + ステータスで構築できる" $
      case mkCustomsDeclaration bid "123456" "ABC 通関" Pending of
        Right cd -> do
          cdBookingId cd `shouldBe` bid
          unHsCode (cdHsCode cd) `shouldBe` "123456"
          cdBrokerName cd `shouldBe` "ABC 通関"
          cdStatus cd `shouldBe` Pending
        Left e -> expectationFailure ("expected Right but got " <> show e)

    it "HS コード形式エラーは Left InvalidHsCode" $
      mkCustomsDeclaration bid "BAD" "ABC 通関" Pending
        `shouldBe` Left (InvalidHsCode "BAD")

    it "通関業者名が空は Left InvalidBrokerName" $
      mkCustomsDeclaration bid "123456" "  " Pending
        `shouldBe` Left (InvalidBrokerName "通関業者名は必須です")

    it "通関業者名 100 文字超は Left InvalidBrokerName" $
      mkCustomsDeclaration bid "123456" (T.replicate 101 "あ") Pending
        `shouldBe` Left (InvalidBrokerName "通関業者名は 100 文字以内で入力してください")
