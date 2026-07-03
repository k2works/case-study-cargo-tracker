{-# LANGUAGE OverloadedStrings #-}

-- | DetailJsonParser のテスト (US19/US20, IT7)
module Exception.Infrastructure.DetailJsonParserSpec (spec) where

import Test.Hspec

import Cargotracker.Exception.Domain.Model.Amount (Amount (..))
import Cargotracker.Exception.Domain.Model.DamageException (DamageException (..))
import Cargotracker.Exception.Domain.Model.DelayException (DelayException (..))
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.LossException (LossException (..))
import Cargotracker.Exception.Infrastructure.DetailJsonParser (parseDetailJson)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

spec :: Spec
spec = describe "parseDetailJson (US19/US20, IT7)" $ do
  describe "Delay" $ do
    it "delayHours + reason を復元する" $ do
      let result =
            parseDetailJson "DELAY" "{\"delayHours\":48,\"reason\":\"港湾ストライキ\"}"
      case result of
        Right (Delay d) -> do
          deDelayHours d `shouldBe` 48
          deReason d `shouldBe` "港湾ストライキ"
        other -> expectationFailure ("expected Delay, got " <> show other)

    it "小文字 delay も受理する (case-insensitive)" $
      case parseDetailJson "delay" "{\"delayHours\":24,\"reason\":\"a\"}" of
        Right (Delay _) -> pure ()
        other -> expectationFailure ("expected Delay, got " <> show other)

    it "delayHours <= 0 は InvalidDelayHours を伝播" $
      parseDetailJson "DELAY" "{\"delayHours\":0,\"reason\":\"x\"}"
        `shouldBe` Left (InvalidDelayHours 0)

  describe "Damage" $ do
    it "amount + currency + description を復元する" $ do
      let raw = "{\"amount\":500000,\"currency\":\"JPY\",\"description\":\"破損\"}"
      case parseDetailJson "DAMAGE" raw of
        Right (Damage d) -> do
          amValue (daAmount d) `shouldBe` 500000
          amCurrency (daAmount d) `shouldBe` "JPY"
          daDescription d `shouldBe` "破損"
        other -> expectationFailure ("expected Damage, got " <> show other)

    it "負の amount は InvalidCost を伝播" $
      parseDetailJson "DAMAGE" "{\"amount\":-1,\"currency\":\"JPY\",\"description\":\"x\"}"
        `shouldBe` Left (InvalidCost (-1))

  describe "Loss" $ do
    it "lastSeenAt が UN/LOCODE の Loss を復元する" $
      case parseDetailJson "LOSS" "{\"amount\":1200000,\"currency\":\"USD\",\"lastSeenAt\":\"USSEA\"}" of
        Right (Loss l) -> do
          amValue (loAmount l) `shouldBe` 1200000
          loLastSeenAt l `shouldBe` Just "USSEA"
        other -> expectationFailure ("expected Loss, got " <> show other)

    it "lastSeenAt が null / 未指定は Nothing" $
      case parseDetailJson "LOSS" "{\"amount\":100,\"currency\":\"JPY\",\"lastSeenAt\":null}" of
        Right (Loss l) -> loLastSeenAt l `shouldBe` Nothing
        other -> expectationFailure ("expected Loss, got " <> show other)

  describe "エラー系" $ do
    it "未知の type は InvalidExceptionReason \"unknown exception type\"" $
      parseDetailJson "URGENT" "{}"
        `shouldBe` Left (InvalidExceptionReason "unknown exception type")

    it "壊れた JSON は InvalidExceptionReason \"malformed detail json\"" $
      parseDetailJson "DELAY" "{not json"
        `shouldBe` Left (InvalidExceptionReason "malformed detail json")

    it "必須キー欠落は malformed 扱い" $
      parseDetailJson "DELAY" "{\"delayHours\":24}"
        `shouldBe` Left (InvalidExceptionReason "malformed detail json")
