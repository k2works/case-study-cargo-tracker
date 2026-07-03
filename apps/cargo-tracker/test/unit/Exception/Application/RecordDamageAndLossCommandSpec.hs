-- | RecordDamageExceptionCommand / RecordLossExceptionCommand のテスト (US20, IT7)
module Exception.Application.RecordDamageAndLossCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Text (Text)
import Data.Time (UTCTime, fromGregorian, secondsToDiffTime)
import qualified Data.Time as Time
import Test.Hspec

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import qualified Cargotracker.Exception.Application.RecordDamageExceptionCommand as Damage
import qualified Cargotracker.Exception.Application.RecordLossExceptionCommand as Loss
import Cargotracker.Exception.Domain.Model.Amount (Amount (..))
import Cargotracker.Exception.Domain.Model.DamageException (DamageException (..))
import Cargotracker.Exception.Domain.Model.ExceptionRecord (ExceptionRecord (..))
import Cargotracker.Exception.Domain.Model.ExceptionSeverity (Level (..))
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.LossException (LossException (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

reportedAt :: UTCTime
reportedAt = Time.UTCTime (fromGregorian 2026 9 28) (secondsToDiffTime 3600)

makeRepo :: IO (ExceptionRepository IO, IO [ExceptionRecord])
makeRepo = do
  ref <- newIORef ([] :: [ExceptionRecord])
  let repo =
        ExceptionRepository
          { saveException = \r -> do
              modifyIORef' ref (r :)
              pure (Right ())
          , findExceptionById = \_ -> pure Nothing
          , findExceptionsByTrackingNumber = \_ -> pure []
          , updateExceptionResolution = \_ _ -> pure (Right ())
          }
  pure (repo, readIORef ref)

validDamageInput :: Damage.RecordDamageExceptionInput
validDamageInput =
  Damage.RecordDamageExceptionInput
    { Damage.inputExceptionId = "EX-D001"
    , Damage.inputTrackingNumber = "TR000001"
    , Damage.inputAmountValue = 500000
    , Damage.inputAmountCurrency = "JPY"
    , Damage.inputDescription = "冷凍コンテナ温度制御故障"
    , Damage.inputSeverityLevel = Critical
    , Damage.inputReporterUserId = "handler-1"
    , Damage.inputReporterRole = "Handler"
    , Damage.inputReportedAt = reportedAt
    }

validLossInput :: Loss.RecordLossExceptionInput
validLossInput =
  Loss.RecordLossExceptionInput
    { Loss.inputExceptionId = "EX-L001"
    , Loss.inputTrackingNumber = "TR000002"
    , Loss.inputAmountValue = 1200000
    , Loss.inputAmountCurrency = "USD"
    , Loss.inputLastSeenAt = Just "USSEA"
    , Loss.inputSeverityLevel = High
    , Loss.inputReporterUserId = "tracker-1"
    , Loss.inputReporterRole = "Tracker"
    , Loss.inputReportedAt = reportedAt
    }

spec :: Spec
spec = do
  describe "RecordDamageExceptionCommand.execute (US20)" $ do
    it "正常系: DamageException 集約が構築され永続化される" $ do
      (repo, getSaved) <- makeRepo
      result <- Damage.execute repo validDamageInput
      case result of
        Right r -> do
          erExceptionId r `shouldBe` "EX-D001"
          case erType r of
            Damage d -> do
              amValue (daAmount d) `shouldBe` 500000
              amCurrency (daAmount d) `shouldBe` "JPY"
              daDescription d `shouldBe` "冷凍コンテナ温度制御故障"
            other -> expectationFailure ("expected Damage, got " <> show other)
        Left e -> expectationFailure ("expected Right, got " <> show e)
      saved <- getSaved
      length saved `shouldBe` 1

    it "負の損害額は InvalidCost、永続化されない" $ do
      (repo, getSaved) <- makeRepo
      result <- Damage.execute repo (validDamageInput {Damage.inputAmountValue = -1})
      result `shouldBe` Left (InvalidCost (-1))
      saved <- getSaved
      length saved `shouldBe` 0

    it "空 description は InvalidExceptionReason \"empty\"、永続化されない" $ do
      (repo, getSaved) <- makeRepo
      result <- Damage.execute repo (validDamageInput {Damage.inputDescription = ""})
      result `shouldBe` Left (InvalidExceptionReason "empty")
      saved <- getSaved
      length saved `shouldBe` 0

    it "小文字通貨は InvalidCurrency" $ do
      (repo, _) <- makeRepo
      result <- Damage.execute repo (validDamageInput {Damage.inputAmountCurrency = "jpy"})
      result `shouldBe` Left (InvalidCurrency "jpy")

  describe "RecordLossExceptionCommand.execute (US20)" $ do
    it "正常系: 5 文字 lastSeenAt を持つ LossException が永続化される" $ do
      (repo, getSaved) <- makeRepo
      result <- Loss.execute repo validLossInput
      case result of
        Right r -> do
          erExceptionId r `shouldBe` "EX-L001"
          case erType r of
            Loss l -> do
              amValue (loAmount l) `shouldBe` 1200000
              amCurrency (loAmount l) `shouldBe` "USD"
              loLastSeenAt l `shouldBe` Just "USSEA"
            other -> expectationFailure ("expected Loss, got " <> show other)
        Left e -> expectationFailure ("expected Right, got " <> show e)
      saved <- getSaved
      length saved `shouldBe` 1

    it "Nothing lastSeenAt (不明) も受理される" $ do
      (repo, _) <- makeRepo
      let input = validLossInput {Loss.inputLastSeenAt = Nothing}
      result <- Loss.execute repo input
      case result of
        Right r -> case erType r of
          Loss l -> loLastSeenAt l `shouldBe` Nothing
          other -> expectationFailure ("expected Loss, got " <> show other)
        Left e -> expectationFailure ("expected Right, got " <> show e)

    it "6 文字 lastSeenAt は InvalidExceptionReason \"invalid unlocode\"" $ do
      (repo, getSaved) <- makeRepo
      let input = validLossInput {Loss.inputLastSeenAt = Just ("USSEA1" :: Text)}
      result <- Loss.execute repo input
      result `shouldBe` Left (InvalidExceptionReason "invalid unlocode")
      saved <- getSaved
      length saved `shouldBe` 0

    it "空 reporter role は InvalidReporter" $ do
      (repo, _) <- makeRepo
      result <- Loss.execute repo (validLossInput {Loss.inputReporterRole = ""})
      result `shouldBe` Left (InvalidReporter "empty role")
