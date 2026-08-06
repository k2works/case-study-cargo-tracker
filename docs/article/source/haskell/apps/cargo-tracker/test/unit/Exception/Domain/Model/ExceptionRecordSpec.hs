-- | Reporter + ExceptionRecord のテスト (US19/US20, IT7)
module Exception.Domain.Model.ExceptionRecordSpec (spec) where

import Data.Time (UTCTime, addUTCTime, fromGregorian, secondsToDiffTime)
import qualified Data.Time as Time
import Test.Hspec

import Cargotracker.Exception.Domain.Model.Amount (mkAmount)
import Cargotracker.Exception.Domain.Model.DelayException (mkDelayException)
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord (..),
    isResolved,
    mkExceptionRecord,
    resolveException,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    Level (..),
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.LossException (mkLossException)
import Cargotracker.Exception.Domain.Model.Reporter (Reporter (..), mkReporter)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

reportedAt :: UTCTime
reportedAt = Time.UTCTime (fromGregorian 2026 9 28) (secondsToDiffTime 3600)

laterAt :: UTCTime
laterAt = addUTCTime 3600 reportedAt

sampleReporter :: Reporter
sampleReporter = case mkReporter "user-42" "Tracker" of
  Right r -> r
  Left _ -> error "test setup: invalid reporter"

sampleDelayType :: ExceptionType
sampleDelayType = case mkDelayException 24 "港湾遅延" of
  Right d -> Delay d
  Left _ -> error "test setup: invalid delay"

sampleLossType :: ExceptionType
sampleLossType = case mkAmount 500000 "JPY" of
  Right a -> case mkLossException a Nothing of
    Right l -> Loss l
    Left _ -> error "test setup: invalid loss"
  Left _ -> error "test setup: invalid amount"

buildRecord :: IO ExceptionRecord
buildRecord =
  case mkExceptionRecord
    "EX-0001"
    sampleDelayType
    (ExceptionSeverity High)
    sampleReporter
    reportedAt
    "TR000001" of
    Right r -> pure r
    Left e -> error ("test setup: " <> show e)

spec :: Spec
spec = do
  describe "mkReporter (US19/US20, IT7)" $ do
    it "userId と role を trim して受理" $
      mkReporter "  user-1  " "  Handler  "
        `shouldBe` Right (Reporter "user-1" "Handler")

    it "空文字 userId は InvalidReporter \"empty user id\"" $
      mkReporter "" "Handler" `shouldBe` Left (InvalidReporter "empty user id")

    it "空白のみ userId は InvalidReporter \"empty user id\"" $
      mkReporter "  " "Handler" `shouldBe` Left (InvalidReporter "empty user id")

    it "空文字 role は InvalidReporter \"empty role\"" $
      mkReporter "user-1" "" `shouldBe` Left (InvalidReporter "empty role")

  describe "mkExceptionRecord (US19/US20, IT7)" $ do
    it "正常系: 未解決状態 (erResolvedAt = Nothing) で構築" $ do
      r <- buildRecord
      erExceptionId r `shouldBe` "EX-0001"
      erTrackingNumber r `shouldBe` "TR000001"
      erResolvedAt r `shouldBe` Nothing
      isResolved r `shouldBe` False

    it "任意の ExceptionType (Delay/Damage/Loss) で構築可能" $ do
      case mkExceptionRecord
        "EX-0002"
        sampleLossType
        (ExceptionSeverity Critical)
        sampleReporter
        reportedAt
        "TR000002" of
        Right r -> case erType r of
          Loss _ -> pure ()
          other -> expectationFailure ("expected Loss, got " <> show other)
        Left e -> expectationFailure ("expected Right, got " <> show e)

    it "空文字 exceptionId は InvalidExceptionReason \"empty exception id\"" $ do
      let result =
            mkExceptionRecord
              ""
              sampleDelayType
              (ExceptionSeverity Low)
              sampleReporter
              reportedAt
              "TR000001"
      result `shouldBe` Left (InvalidExceptionReason "empty exception id")

    it "空文字 trackingNumber は InvalidExceptionReason \"empty tracking number\"" $ do
      let result =
            mkExceptionRecord
              "EX-0003"
              sampleDelayType
              (ExceptionSeverity Low)
              sampleReporter
              reportedAt
              "   "
      result `shouldBe` Left (InvalidExceptionReason "empty tracking number")

  describe "resolveException (US19/US20)" $ do
    it "未解決レコードは解決済に遷移し erResolvedAt が設定される" $ do
      r <- buildRecord
      case resolveException laterAt r of
        Right r' -> do
          erResolvedAt r' `shouldBe` Just laterAt
          isResolved r' `shouldBe` True
        Left e -> expectationFailure ("expected Right, got " <> show e)

    it "既に解決済のレコードは ExceptionAlreadyResolved" $ do
      r <- buildRecord
      case resolveException laterAt r of
        Right r' -> resolveException laterAt r' `shouldBe` Left ExceptionAlreadyResolved
        Left e -> expectationFailure ("expected Right, got " <> show e)

    it "resolveException は他のフィールドを変更しない" $ do
      r <- buildRecord
      case resolveException laterAt r of
        Right r' -> do
          erExceptionId r' `shouldBe` erExceptionId r
          erTrackingNumber r' `shouldBe` erTrackingNumber r
          erSeverity r' `shouldBe` erSeverity r
          erReporter r' `shouldBe` erReporter r
          erReportedAt r' `shouldBe` erReportedAt r
        Left e -> expectationFailure ("expected Right, got " <> show e)
