-- | CreateEstimateCommand のテスト (US01, IT2)
module Estimation.Application.CreateEstimateCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Estimation.Application.CreateEstimateCommand
  ( CandidateInput (..),
    CreateEstimateInput (..),
    execute,
  )
import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate (..),
  )
import Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate (..),
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateStatus
  ( EstimateStatus (..),
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

deadlineAt :: UTCTime
deadlineAt = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

validCandidate :: CandidateInput
validCandidate =
  CandidateInput
    { inputRank = 0
    , inputTransitDays = 14
    , inputEstimatedCost = 100000
    , inputVoyageNumbers = ["V0001"]
    }

validInput :: CreateEstimateInput
validInput =
  CreateEstimateInput
    { inputEstimateId = "550e8400-e29b-41d4-a716-446655440000"
    , inputShipperId = "SHP-A1B2C3"
    , inputOrigin = "JPTYO"
    , inputDestination = "USNYC"
    , inputDeadline = deadlineAt
    , inputCargoType = "GENERAL"
    , inputWeightKg = 500.0
    , inputCandidates = [validCandidate]
    }

makeRepo :: IO (EstimateRepository IO, IO [Estimate])
makeRepo = do
  ref <- newIORef ([] :: [Estimate])
  let r =
        EstimateRepository
          { saveEstimate = \e -> do
              modifyIORef' ref (e :)
              pure (Right ())
          , findEstimateById = \_ -> do
              xs <- readIORef ref
              pure $ case xs of (x : _) -> Just x; [] -> Nothing
          }
  pure (r, readIORef ref)

spec :: Spec
spec = describe "CreateEstimateCommand" $ do
  it "正常入力で Estimate を Created 状態で永続化する" $ do
    (repo, get) <- makeRepo
    result <- execute repo validInput
    case result of
      Right e -> do
        estimateStatus e `shouldBe` Created
        length (routeCandidates e) `shouldBe` 1
      Left err -> expectationFailure ("expected Right but got " <> show err)
    saved <- get
    length saved `shouldBe` 1

  it "候補ゼロ件 (期限内到達不可) でも Right で保存される" $ do
    (repo, _) <- makeRepo
    let i = validInput {inputCandidates = []}
    result <- execute repo i
    case result of
      Right e -> routeCandidates e `shouldBe` []
      Left err -> expectationFailure ("expected Right but got " <> show err)

  it "EstimateId が UUID 形式でないと Left InvalidBookingId" $ do
    (repo, _) <- makeRepo
    let i = validInput {inputEstimateId = "not-a-uuid"}
    result <- execute repo i
    case result of
      Left (InvalidBookingId _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)

  it "出発港の UnLocode が不正は Left" $ do
    (repo, _) <- makeRepo
    let i = validInput {inputOrigin = "ABC"}
    result <- execute repo i
    case result of
      Left (InvalidUnLocode _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)

  it "候補の rank 重複は Left" $ do
    (repo, _) <- makeRepo
    let dup = validCandidate {inputRank = 0, inputEstimatedCost = 80000}
        i = validInput {inputCandidates = [validCandidate, dup]}
    result <- execute repo i
    case result of
      Left (InvalidBookingId _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)

  it "Repository が ConcurrentModification を返したら伝播する" $ do
    let repo =
          EstimateRepository
            { saveEstimate = \_ -> pure (Left (ConcurrentModification "uuid"))
            , findEstimateById = \_ -> pure Nothing
            }
    result <- execute repo validInput
    result `shouldBe` Left (ConcurrentModification "uuid")
