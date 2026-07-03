-- | RecordDelayExceptionCommand のテスト (US19, IT7)
module Exception.Application.RecordDelayExceptionCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Time (UTCTime, fromGregorian, secondsToDiffTime)
import qualified Data.Time as Time
import Test.Hspec

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Application.RecordDelayExceptionCommand
  ( RecordDelayExceptionInput (..),
    execute,
  )
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord (..),
    isResolved,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity (Level (..))
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.Reporter (Reporter (..))
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
          , findExceptionById = \eid -> do
              xs <- readIORef ref
              pure (case [r | r <- xs, erExceptionId r == eid] of (x : _) -> Just x; [] -> Nothing)
          , findExceptionsByTrackingNumber = \tn -> do
              xs <- readIORef ref
              pure [r | r <- xs, erTrackingNumber r == tn]
          , updateExceptionResolution = \_ _ -> pure (Right ())
          }
  pure (repo, readIORef ref)

validInput :: RecordDelayExceptionInput
validInput =
  RecordDelayExceptionInput
    { inputExceptionId = "EX-0001"
    , inputTrackingNumber = "TR000001"
    , inputDelayHours = 48
    , inputReason = "港湾ストライキ"
    , inputSeverityLevel = High
    , inputReporterUserId = "user-42"
    , inputReporterRole = "Handler"
    , inputReportedAt = reportedAt
    }

spec :: Spec
spec = describe "RecordDelayExceptionCommand.execute (US19, IT7)" $ do
  it "正常系: ExceptionRecord が構築され Repository に永続化される" $ do
    (repo, getSaved) <- makeRepo
    result <- execute repo validInput
    case result of
      Right r -> do
        erExceptionId r `shouldBe` "EX-0001"
        erTrackingNumber r `shouldBe` "TR000001"
        reporterUserId (erReporter r) `shouldBe` "user-42"
        reporterRole (erReporter r) `shouldBe` "Handler"
        isResolved r `shouldBe` False
        case erType r of
          Delay _ -> pure ()
          other -> expectationFailure ("expected Delay, got " <> show other)
      Left e -> expectationFailure ("expected Right, got " <> show e)
    saved <- getSaved
    length saved `shouldBe` 1

  it "0 時間の遅延は InvalidDelayHours、永続化されない" $ do
    (repo, getSaved) <- makeRepo
    result <- execute repo (validInput {inputDelayHours = 0})
    result `shouldBe` Left (InvalidDelayHours 0)
    saved <- getSaved
    length saved `shouldBe` 0

  it "空の理由は InvalidExceptionReason、永続化されない" $ do
    (repo, getSaved) <- makeRepo
    result <- execute repo (validInput {inputReason = "  "})
    result `shouldBe` Left (InvalidExceptionReason "empty")
    saved <- getSaved
    length saved `shouldBe` 0

  it "空 exceptionId は InvalidExceptionReason \"empty exception id\"" $ do
    (repo, getSaved) <- makeRepo
    result <- execute repo (validInput {inputExceptionId = ""})
    result `shouldBe` Left (InvalidExceptionReason "empty exception id")
    saved <- getSaved
    length saved `shouldBe` 0

  it "空 reporter userId は InvalidReporter \"empty user id\"" $ do
    (repo, getSaved) <- makeRepo
    result <- execute repo (validInput {inputReporterUserId = ""})
    result `shouldBe` Left (InvalidReporter "empty user id")
    saved <- getSaved
    length saved `shouldBe` 0

  it "Repository の永続化失敗はエラーを伝播する" $ do
    let failingRepo =
          ExceptionRepository
            { saveException = \_ -> pure (Left (ConcurrentModification "boom"))
            , findExceptionById = \_ -> pure Nothing
            , findExceptionsByTrackingNumber = \_ -> pure []
            , updateExceptionResolution = \_ _ -> pure (Right ())
            }
    result <- execute failingRepo validInput
    case result of
      Left (ConcurrentModification msg) -> msg `shouldBe` "boom"
      other -> expectationFailure ("expected ConcurrentModification, got " <> show other)
