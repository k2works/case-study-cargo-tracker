-- | UpdateVoyageCommand のテスト (US25, IT2)
module Routing.Application.UpdateVoyageCommandSpec (spec) where

import Data.IORef (newIORef, readIORef, writeIORef)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Application.RegisterVoyageCommand
  ( CarrierMovementInput (..),
  )
import Cargotracker.Routing.Application.UpdateVoyageCommand
  ( UpdateVoyageInput (..),
    execute,
  )
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber (..),
    mkVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..), mkVoyage)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 7 1) (secondsToDiffTime 0)

oneDay :: UTCTime -> UTCTime
oneDay = addUTCTime 86400

-- 既存 Voyage を構築するヘルパ (JPTYO → USNYC の単一区間)
seedVoyage :: Voyage
seedVoyage =
  let Right vn = mkVoyageNumber "V0001"
      Right v =
        mkVoyage
          vn
          [ CarrierMovement
              { departureLocation = UnLocode "JPTYO"
              , arrivalLocation = UnLocode "USNYC"
              , departureTime = t0
              , arrivalTime = oneDay t0
              }
          ]
   in v

validUpdateInput :: UpdateVoyageInput
validUpdateInput =
  UpdateVoyageInput
    { inputVoyageNumber = "V0001"
    , inputMovements =
        [ CarrierMovementInput
            { inputDeparture = "JPTYO"
            , inputArrival = "USLAX"
            , inputDepartureTime = t0
            , inputArrivalTime = oneDay t0
            }
        , CarrierMovementInput
            { inputDeparture = "USLAX"
            , inputArrival = "USNYC"
            , inputDepartureTime = oneDay t0
            , inputArrivalTime = oneDay (oneDay t0)
            }
        ]
    }

discontinuousInput :: UpdateVoyageInput
discontinuousInput =
  UpdateVoyageInput
    { inputVoyageNumber = "V0001"
    , inputMovements =
        [ CarrierMovementInput
            { inputDeparture = "JPTYO"
            , inputArrival = "USLAX"
            , inputDepartureTime = t0
            , inputArrivalTime = oneDay t0
            }
        , CarrierMovementInput
            { inputDeparture = "GBLON" -- 前区間の arrival USLAX とつながらない
            , inputArrival = "USNYC"
            , inputDepartureTime = oneDay t0
            , inputArrivalTime = oneDay (oneDay t0)
            }
        ]
    }

makeRepo :: Maybe Voyage -> IO (VoyageRepository IO, IO (Maybe Voyage))
makeRepo seed = do
  updRef <- newIORef Nothing
  let r =
        VoyageRepository
          { findByVoyageNumber = \_ -> pure seed
          , saveVoyage = \_ -> pure ()
          , updateVoyage = \v -> do
              writeIORef updRef (Just v)
              pure (Right ())
          , findAllVoyages = pure []
          }
  pure (r, readIORef updRef)

spec :: Spec
spec = describe "UpdateVoyageCommand (US25)" $ do
  it "既存航海の区間を新しい連続列に更新し version が +1 される" $ do
    (repo, getUpdated) <- makeRepo (Just seedVoyage)
    result <- execute repo validUpdateInput
    case result of
      Right v -> do
        voyageVersion v `shouldBe` 2
        length (carrierMovements v) `shouldBe` 2
      Left e -> expectationFailure ("expected Right but got " <> show e)
    mUpdated <- getUpdated
    case mUpdated of
      Just _ -> pure ()
      Nothing -> expectationFailure "updateVoyage was not called"

  it "対象航海が見つからない場合は InvalidVoyageNumber" $ do
    (repo, _) <- makeRepo Nothing
    result <- execute repo validUpdateInput
    case result of
      Left (InvalidVoyageNumber _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)

  it "区間連続性違反は LegContinuityViolation で updateVoyage は呼ばれない" $ do
    (repo, getUpdated) <- makeRepo (Just seedVoyage)
    result <- execute repo discontinuousInput
    case result of
      Left (LegContinuityViolation _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
    mUpdated <- getUpdated
    mUpdated `shouldBe` Nothing

  it "Repository.updateVoyage が ConcurrentModification を返すと伝播する" $ do
    let repo =
          VoyageRepository
            { findByVoyageNumber = \_ -> pure (Just seedVoyage)
            , saveVoyage = \_ -> pure ()
            , updateVoyage = \_ -> pure (Left (ConcurrentModification "V0001"))
            , findAllVoyages = pure []
            }
    result <- execute repo validUpdateInput
    result `shouldBe` Left (ConcurrentModification "V0001")

  it "VoyageNumber が不正なら InvalidVoyageNumber" $ do
    (repo, _) <- makeRepo (Just seedVoyage)
    let bad = validUpdateInput {inputVoyageNumber = ""}
    result <- execute repo bad
    case result of
      Left (InvalidVoyageNumber _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
