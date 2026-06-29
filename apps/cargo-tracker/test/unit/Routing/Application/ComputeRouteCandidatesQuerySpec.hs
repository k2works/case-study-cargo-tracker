{-# LANGUAGE OverloadedStrings #-}

-- | ComputeRouteCandidatesQuery のテスト (US08a タスク 4.3, IT3)
module Routing.Application.ComputeRouteCandidatesQuerySpec (spec) where

import Data.Text (Text)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Application.ComputeRouteCandidatesQuery
  ( ComputeRouteCandidatesInput (..),
    execute,
  )
import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage, mkVoyage)
import Cargotracker.Routing.Domain.Service.RouteFinder (FoundRoute (..))
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 9 1) (secondsToDiffTime 0)

day :: Integer -> UTCTime
day n = addUTCTime (fromIntegral n * 86400) t0

cm :: Text -> Text -> UTCTime -> UTCTime -> CarrierMovement
cm dep arr depT arrT =
  CarrierMovement
    { departureLocation = UnLocode dep
    , arrivalLocation = UnLocode arr
    , departureTime = depT
    , arrivalTime = arrT
    }

mkV :: Text -> [CarrierMovement] -> Voyage
mkV vn ms = case mkVoyageNumber vn of
  Right v -> case mkVoyage v ms of
    Right voy -> voy
    Left e -> error (show e)
  Left e -> error (show e)

makeRepo :: [Voyage] -> VoyageRepository IO
makeRepo voys =
  VoyageRepository
    { findByVoyageNumber = \_ -> pure Nothing
    , saveVoyage = \_ -> pure ()
    , updateVoyage = \_ -> pure (Right ())
    , findAllVoyages = pure voys
    }

baseInput :: ComputeRouteCandidatesInput
baseInput =
  ComputeRouteCandidatesInput
    { inputOrigin = UnLocode "JPTYO"
    , inputDestination = UnLocode "USNYC"
    , inputDeadline = day 30
    }

spec :: Spec
spec = describe "ComputeRouteCandidatesQuery (US08a)" $ do
  it "直行便 + 経由便を rank 順で返す" $ do
    let direct = mkV "VD" [cm "JPTYO" "USNYC" t0 (day 14)]
        leg1 = mkV "VT1" [cm "JPTYO" "SGSIN" t0 (day 7)]
        leg2 = mkV "VT2" [cm "SGSIN" "USNYC" (day 8) (day 21)]
    res <- execute (makeRepo [direct, leg1, leg2]) baseInput
    case res of
      Right rs -> do
        length rs `shouldBe` 2
        frRank (head rs) `shouldBe` 0
        map unVoyageNumber (frVoyageNumbers (head rs)) `shouldBe` ["VD"]
      Left e -> expectationFailure ("expected Right but got " <> show e)

  it "候補ゼロ件のときは Right [] を返す" $ do
    res <- execute (makeRepo []) baseInput
    res `shouldBe` Right []

  it "出発地と目的地が同一なら Left SameOriginDestination" $ do
    let bad = baseInput {inputDestination = inputOrigin baseInput}
    res <- execute (makeRepo []) bad
    case res of
      Left (SameOriginDestination _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
