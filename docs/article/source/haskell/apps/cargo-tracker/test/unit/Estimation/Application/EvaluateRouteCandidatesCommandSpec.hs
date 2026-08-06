-- | EvaluateRouteCandidatesCommand のテスト (US08b, IT4)
module Estimation.Application.EvaluateRouteCandidatesCommandSpec (spec) where

import Data.Text (Text)
import Test.Hspec

import Cargotracker.Estimation.Application.EvaluateRouteCandidatesCommand
  ( EvaluatedRoute (..),
    RouteCandidateInput (..),
    evaluateCandidates,
  )
import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation (..),
    ExclusionReason (..),
    RouteConstraint (..),
    noConstraint,
  )

allowedPorts :: [Text]
allowedPorts = ["JPTYO", "USNYC"]

reeferVoyages :: [Text]
reeferVoyages = ["V001"]

directRoute :: RouteCandidateInput
directRoute =
  RouteCandidateInput
    { rciRank = 0
    , rciPorts = ["JPTYO", "USNYC"]
    , rciVoyages = ["V001"]
    }

hazardousRoute :: RouteCandidateInput
hazardousRoute =
  RouteCandidateInput
    { rciRank = 1
    , rciPorts = ["JPTYO", "HKHKG", "USNYC"]
    , rciVoyages = ["V001"]
    }

reeferFailRoute :: RouteCandidateInput
reeferFailRoute =
  RouteCandidateInput
    { rciRank = 2
    , rciPorts = ["JPTYO", "USNYC"]
    , rciVoyages = ["V001", "V002"]
    }

spec :: Spec
spec = describe "EvaluateRouteCandidatesCommand (US08b / IT4)" $ do
  it "noConstraint なら全候補が accepted" $ do
    let results =
          evaluateCandidates
            noConstraint
            allowedPorts
            reeferVoyages
            [directRoute, hazardousRoute, reeferFailRoute]
    length results `shouldBe` 3
    all (ceAccepted . evEvaluation) results `shouldBe` True

  it "rcHazardous=True で HKHKG を含む経路が rejected、直行便は accepted" $ do
    let results =
          evaluateCandidates
            (RouteConstraint True False False)
            allowedPorts
            reeferVoyages
            [directRoute, hazardousRoute]
        accepted = [r | r <- results, ceAccepted (evEvaluation r)]
        rejected = [r | r <- results, not (ceAccepted (evEvaluation r))]
    length accepted `shouldBe` 1
    rciRank (evCandidate (head accepted)) `shouldBe` 0
    length rejected `shouldBe` 1
    rciRank (evCandidate (head rejected)) `shouldBe` 1
    ceReasons (evEvaluation (head rejected))
      `shouldContain` [HazardousPortViolation "HKHKG"]

  it "rcReeferRequired=True で V002 を含む経路が rejected" $ do
    let results =
          evaluateCandidates
            (RouteConstraint False True False)
            allowedPorts
            reeferVoyages
            [directRoute, reeferFailRoute]
        accepted = [r | r <- results, ceAccepted (evEvaluation r)]
        rejected = [r | r <- results, not (ceAccepted (evEvaluation r))]
    length accepted `shouldBe` 1
    rciRank (evCandidate (head accepted)) `shouldBe` 0
    length rejected `shouldBe` 1
    ceReasons (evEvaluation (head rejected))
      `shouldContain` [ReeferUnavailable "V002"]

  it "rank が入力順を保持する (sort はしない、UI 側責務)" $ do
    let inputs = [reeferFailRoute, directRoute, hazardousRoute] -- 2, 0, 1
        results = evaluateCandidates noConstraint allowedPorts reeferVoyages inputs
    map (rciRank . evCandidate) results `shouldBe` [2, 0, 1]

  it "空入力は空結果" $
    evaluateCandidates noConstraint [] [] [] `shouldBe` []
