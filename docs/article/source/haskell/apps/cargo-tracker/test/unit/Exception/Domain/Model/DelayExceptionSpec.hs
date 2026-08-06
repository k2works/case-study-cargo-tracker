{- | DelayException / ExceptionSeverity のテスト (US19, IT7)

Exception BC の Domain 純粋関数を検証する。
-}
module Exception.Domain.Model.DelayExceptionSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Exception.Domain.Model.DelayException
  ( DelayException (..),
    mkDelayException,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( Level (..),
    levelToText,
    textToLevel,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

spec :: Spec
spec = do
  describe "ExceptionSeverity Level (US19, IT7)" $ do
    it "Low < Medium < High < Critical の順序" $ do
      compare Low Medium `shouldBe` LT
      compare Medium High `shouldBe` LT
      compare High Critical `shouldBe` LT

    it "Level は Enum / Bounded を満たす" $
      [minBound .. maxBound] `shouldBe` [Low, Medium, High, Critical]

    it "levelToText: 4 値の Text 変換" $ do
      levelToText Low `shouldBe` "LOW"
      levelToText Medium `shouldBe` "MEDIUM"
      levelToText High `shouldBe` "HIGH"
      levelToText Critical `shouldBe` "CRITICAL"

    it "textToLevel: 正しい 4 値と不正値" $ do
      textToLevel "LOW" `shouldBe` Just Low
      textToLevel "CRITICAL" `shouldBe` Just Critical
      textToLevel "critical" `shouldBe` Nothing
      textToLevel "URGENT" `shouldBe` Nothing

  describe "mkDelayException (US19, IT7)" $ do
    it "正の遅延時間と非空理由を受理する" $ do
      let result = mkDelayException 48 "港湾ストライキ"
      result `shouldBe` Right (DelayException 48 "港湾ストライキ")

    it "0 時間は InvalidDelayHours" $
      mkDelayException 0 "理由" `shouldBe` Left (InvalidDelayHours 0)

    it "負の時間は InvalidDelayHours" $
      mkDelayException (-3) "理由" `shouldBe` Left (InvalidDelayHours (-3))

    it "空文字理由は InvalidExceptionReason \"empty\"" $
      mkDelayException 24 "" `shouldBe` Left (InvalidExceptionReason "empty")

    it "空白のみ理由は InvalidExceptionReason \"empty\"" $
      mkDelayException 24 "   " `shouldBe` Left (InvalidExceptionReason "empty")

    it "500 文字ちょうどは受理する (境界)" $ do
      let reason = T.replicate 500 "a"
      case mkDelayException 12 reason of
        Right (DelayException _ r) -> T.length r `shouldBe` 500
        other -> expectationFailure ("expected Right, got " <> show other)

    it "501 文字は InvalidExceptionReason \"too long\"" $ do
      let reason = T.replicate 501 "a"
      mkDelayException 12 reason `shouldBe` Left (InvalidExceptionReason "too long")

    it "理由の前後空白は trim される" $ do
      let result = mkDelayException 6 "  遅延理由  "
      case result of
        Right (DelayException _ r) -> r `shouldBe` "遅延理由"
        other -> expectationFailure ("expected Right, got " <> show other)
