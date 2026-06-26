{- | RegisterVoyageCommand のテスト (IT1 US24 4.2)

入力:
- 航海番号 (Text)
- 区間リスト ([CarrierMovementInput])
  各区間 = 出発港 / 到着港 (Text), 出発時刻 / 到着時刻 (UTCTime)

フロー:
1. 各値オブジェクトを構築 (UnLocode 検証)
2. CarrierMovement リストに変換
3. mkVoyage で集約 + 区間連続性検証
4. Repository.save で永続化

重複検出: 同じ航海番号は既存ありなら ConcurrentModification。
-}
module Routing.Application.RegisterVoyageCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Application.RegisterVoyageCommand
  ( CarrierMovementInput (..),
    RegisterVoyageInput (..),
    execute,
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber (..))
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

ts :: Integer -> UTCTime
ts hour =
  UTCTime
    (fromGregorian 2026 12 1)
    (secondsToDiffTime (hour * 3600))

makeRepo :: [Voyage] -> IO (VoyageRepository IO, IO [Voyage])
makeRepo initial = do
  ref <- newIORef initial
  let r =
        VoyageRepository
          { findByVoyageNumber = \vn -> do
              xs <- readIORef ref
              pure
                ( case [v | v <- xs, voyageNumber v == vn] of
                    (x : _) -> Just x
                    [] -> Nothing
                )
          , saveVoyage = \v -> modifyIORef' ref (v :)
          }
  pure (r, readIORef ref)

validInput :: RegisterVoyageInput
validInput =
  RegisterVoyageInput
    { inputVoyageNumber = "V0001"
    , inputMovements =
        [ CarrierMovementInput
            { inputDeparture = "JPTYO"
            , inputArrival = "USNYC"
            , inputDepartureTime = ts 1
            , inputArrivalTime = ts 24
            }
        ]
    }

spec :: Spec
spec = do
  describe "execute (Happy)" $ do
    it "1 区間の航海を保存する" $ do
      (repo, get) <- makeRepo []
      result <- execute repo validInput
      case result of
        Right vn -> do
          vn `shouldBe` VoyageNumber "V0001"
          saved <- get
          length saved `shouldBe` 1
        Left e -> expectationFailure ("expected Right but got " <> show e)

  describe "execute (バリデーション)" $ do
    it "VoyageNumber が空なら InvalidVoyageNumber" $ do
      (repo, _) <- makeRepo []
      result <- execute repo validInput {inputVoyageNumber = ""}
      result `shouldBe` Left (InvalidVoyageNumber "empty")

    it "出発港が不正な UN/LOCODE なら InvalidUnLocode" $ do
      (repo, _) <- makeRepo []
      let bad =
            [ CarrierMovementInput
                { inputDeparture = "ABC"
                , inputArrival = "USNYC"
                , inputDepartureTime = ts 1
                , inputArrivalTime = ts 24
                }
            ]
      result <- execute repo validInput {inputMovements = bad}
      result `shouldBe` Left (InvalidUnLocode "expected 5 chars")

    it "区間 0 件は LegContinuityViolation" $ do
      (repo, _) <- makeRepo []
      result <- execute repo validInput {inputMovements = []}
      result `shouldBe` Left (LegContinuityViolation "at least 1 movement required")

    it "区間連続性違反は LegContinuityViolation" $ do
      (repo, _) <- makeRepo []
      let bad =
            [ CarrierMovementInput "JPTYO" "HKHKG" (ts 1) (ts 12)
            , CarrierMovementInput "USNYC" "JPTYO" (ts 13) (ts 25) -- 連続性違反
            ]
      result <- execute repo validInput {inputMovements = bad}
      case result of
        Left (LegContinuityViolation _) -> pure ()
        other -> expectationFailure ("expected LegContinuityViolation but got " <> show other)

  describe "execute (重複検出)" $ do
    it "同じ航海番号が既存なら ConcurrentModification" $ do
      (repo, _) <- makeRepo []
      _ <- execute repo validInput
      result <- execute repo validInput
      case result of
        Left (ConcurrentModification _) -> pure ()
        other -> expectationFailure ("expected ConcurrentModification but got " <> show other)
