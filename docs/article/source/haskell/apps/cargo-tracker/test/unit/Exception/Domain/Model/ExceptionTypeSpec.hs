-- | Amount / DamageException / LossException / ExceptionType のテスト (US19/US20, IT7)
module Exception.Domain.Model.ExceptionTypeSpec (spec) where

import Test.Hspec

import Cargotracker.Exception.Domain.Model.Amount (Amount (..), mkAmount)
import Cargotracker.Exception.Domain.Model.DamageException
  ( DamageException (..),
    mkDamageException,
  )
import Cargotracker.Exception.Domain.Model.DelayException (mkDelayException)
import Cargotracker.Exception.Domain.Model.ExceptionType
  ( ExceptionType (..),
    exceptionTypeToText,
  )
import Cargotracker.Exception.Domain.Model.LossException
  ( LossException (..),
    mkLossException,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

sampleAmount :: Amount
sampleAmount = case mkAmount 100000 "JPY" of
  Right a -> a
  Left _ -> error "test setup: invalid amount"

spec :: Spec
spec = do
  describe "mkAmount (US20, IT7)" $ do
    it "0 円 JPY を受理する" $
      mkAmount 0 "JPY" `shouldBe` Right (Amount 0 "JPY")

    it "正の金額と USD を受理する" $
      mkAmount 1500 "USD" `shouldBe` Right (Amount 1500 "USD")

    it "負値は InvalidCost" $
      mkAmount (-1) "JPY" `shouldBe` Left (InvalidCost (-1))

    it "小文字通貨は InvalidCurrency" $
      mkAmount 100 "jpy" `shouldBe` Left (InvalidCurrency "jpy")

    it "2 文字通貨は InvalidCurrency" $
      mkAmount 100 "JP" `shouldBe` Left (InvalidCurrency "JP")

    it "4 文字通貨は InvalidCurrency" $
      mkAmount 100 "JPYY" `shouldBe` Left (InvalidCurrency "JPYY")

  describe "mkDamageException (US20)" $ do
    it "正常系: 金額と説明を受理" $ do
      let result = mkDamageException sampleAmount "冷凍コンテナ故障"
      result `shouldBe` Right (DamageException sampleAmount "冷凍コンテナ故障")

    it "空文字説明は InvalidExceptionReason \"empty\"" $
      mkDamageException sampleAmount ""
        `shouldBe` Left (InvalidExceptionReason "empty")

    it "空白のみの説明も InvalidExceptionReason \"empty\"" $
      mkDamageException sampleAmount "   "
        `shouldBe` Left (InvalidExceptionReason "empty")

    it "説明の前後空白は trim される" $ do
      case mkDamageException sampleAmount "  破損詳細  " of
        Right (DamageException _ d) -> d `shouldBe` "破損詳細"
        other -> expectationFailure ("expected Right, got " <> show other)

  describe "mkLossException (US20)" $ do
    it "Nothing lastSeenAt は受理する (不明ケース)" $
      mkLossException sampleAmount Nothing
        `shouldBe` Right (LossException sampleAmount Nothing)

    it "正しい 5 文字 UN/LOCODE を受理する" $
      mkLossException sampleAmount (Just "JPTYO")
        `shouldBe` Right (LossException sampleAmount (Just "JPTYO"))

    it "空文字 lastSeenAt は InvalidExceptionReason \"empty last seen\"" $
      mkLossException sampleAmount (Just "")
        `shouldBe` Left (InvalidExceptionReason "empty last seen")

    it "6 文字 lastSeenAt は InvalidExceptionReason \"invalid unlocode\"" $
      mkLossException sampleAmount (Just "JPTYO1")
        `shouldBe` Left (InvalidExceptionReason "invalid unlocode")

  describe "ExceptionType exceptionTypeToText" $ do
    it "Delay は DELAY" $ do
      case mkDelayException 24 "遅延" of
        Right d -> exceptionTypeToText (Delay d) `shouldBe` "DELAY"
        Left _ -> expectationFailure "test setup"

    it "Damage は DAMAGE" $ do
      case mkDamageException sampleAmount "破損" of
        Right d -> exceptionTypeToText (Damage d) `shouldBe` "DAMAGE"
        Left _ -> expectationFailure "test setup"

    it "Loss は LOSS" $ do
      case mkLossException sampleAmount Nothing of
        Right l -> exceptionTypeToText (Loss l) `shouldBe` "LOSS"
        Left _ -> expectationFailure "test setup"
