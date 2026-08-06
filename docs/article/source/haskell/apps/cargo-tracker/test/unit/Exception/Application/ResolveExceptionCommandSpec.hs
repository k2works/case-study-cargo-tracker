-- | ResolveExceptionCommand のテスト (US19/US20, IT7)
module Exception.Application.ResolveExceptionCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Time (UTCTime, addUTCTime, fromGregorian, secondsToDiffTime)
import qualified Data.Time as Time
import Test.Hspec

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Application.ResolveExceptionCommand
  ( ResolveExceptionInput (..),
    execute,
  )
import Cargotracker.Exception.Domain.Model.DelayException (mkDelayException)
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord (..),
    isResolved,
    mkExceptionRecord,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    Level (..),
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.Reporter (mkReporter)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

reportedAt :: UTCTime
reportedAt = Time.UTCTime (fromGregorian 2026 9 28) (secondsToDiffTime 3600)

resolvedAt :: UTCTime
resolvedAt = addUTCTime 7200 reportedAt

sampleRecord :: ExceptionRecord
sampleRecord =
  case mkDelayException 24 "港湾遅延" of
    Right d -> case mkReporter "user-1" "Tracker" of
      Right r -> case mkExceptionRecord "EX-0001" (Delay d) (ExceptionSeverity Medium) r reportedAt "TR000001" of
        Right rec -> rec
        Left e -> error ("test setup: " <> show e)
      Left e -> error ("test setup: " <> show e)
    Left e -> error ("test setup: " <> show e)

makeRepo :: [ExceptionRecord] -> IO (ExceptionRepository IO, IO [ExceptionRecord])
makeRepo initial = do
  ref <- newIORef initial
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
          , updateExceptionResolution = \eid updated -> do
              modifyIORef' ref (map (\r -> if erExceptionId r == eid then updated else r))
              pure (Right ())
          }
  pure (repo, readIORef ref)

spec :: Spec
spec = describe "ResolveExceptionCommand.execute (US19/US20, IT7)" $ do
  it "正常系: 未解決レコードを解決済に更新する" $ do
    (repo, getAll) <- makeRepo [sampleRecord]
    result <- execute repo (ResolveExceptionInput "EX-0001" resolvedAt)
    case result of
      Right updated -> do
        isResolved updated `shouldBe` True
        erResolvedAt updated `shouldBe` Just resolvedAt
      Left e -> expectationFailure ("expected Right, got " <> show e)
    stored <- getAll
    case [r | r <- stored, erExceptionId r == "EX-0001"] of
      [r] -> isResolved r `shouldBe` True
      _ -> expectationFailure "expected 1 stored record"

  it "存在しない exceptionId は InvalidExceptionReason \"not found\"" $ do
    (repo, _) <- makeRepo []
    result <- execute repo (ResolveExceptionInput "EX-NONE" resolvedAt)
    result `shouldBe` Left (InvalidExceptionReason "not found")

  it "既に解決済のレコードは ExceptionAlreadyResolved" $ do
    (repo, _) <- makeRepo [sampleRecord]
    _ <- execute repo (ResolveExceptionInput "EX-0001" resolvedAt)
    result <- execute repo (ResolveExceptionInput "EX-0001" resolvedAt)
    result `shouldBe` Left ExceptionAlreadyResolved
