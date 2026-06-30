-- | RouteEvaluator のテスト (US08b, IT4)
module Estimation.Domain.Service.RouteEvaluatorSpec (spec) where

import qualified Data.Set as Set
import Data.Text (Text)
import Test.Hspec

import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation (..),
    ExclusionReason (..),
    RouteConstraint (..),
    noConstraint,
  )
import Cargotracker.Estimation.Domain.Service.RouteEvaluator
  ( EvaluationInput (..),
    evaluate,
  )

inputFor :: [Text] -> [Text] -> EvaluationInput
inputFor ports voyages =
  EvaluationInput
    { eiRoutePorts = ports
    , eiRouteVoyages = voyages
    , eiHazardousAllowedPorts = Set.fromList ["JPTYO", "USNYC"]
    , eiReeferCapableVoyages = Set.fromList ["V001"]
    }

spec :: Spec
spec = describe "RouteEvaluator.evaluate (US08b / IT4)" $ do
  describe "noConstraint (制約なし)" $
    it "すべての経路を採用する" $ do
      let r = evaluate noConstraint (inputFor ["JPTYO", "USNYC"] ["V001"])
      ceAccepted r `shouldBe` True
      ceReasons r `shouldBe` []

  describe "rcHazardous = True (危険物制約)" $ do
    it "全寄港港が許可港なら採用" $ do
      let r = evaluate (RouteConstraint True False False) (inputFor ["JPTYO", "USNYC"] ["V001"])
      ceAccepted r `shouldBe` True

    it "受入不可港 (HKHKG) を含む場合は除外" $ do
      let r = evaluate (RouteConstraint True False False) (inputFor ["JPTYO", "HKHKG", "USNYC"] ["V001"])
      ceAccepted r `shouldBe` False
      ceReasons r `shouldBe` [HazardousPortViolation "HKHKG"]

  describe "rcReeferRequired = True (冷凍制約)" $ do
    it "全 Voyage が冷凍対応なら採用" $ do
      let r = evaluate (RouteConstraint False True False) (inputFor ["JPTYO", "USNYC"] ["V001"])
      ceAccepted r `shouldBe` True

    it "冷凍非対応 Voyage (V002) を含む場合は除外" $ do
      let r = evaluate (RouteConstraint False True False) (inputFor ["JPTYO", "USNYC"] ["V001", "V002"])
      ceAccepted r `shouldBe` False
      ceReasons r `shouldBe` [ReeferUnavailable "V002"]

  describe "複数制約の同時違反" $
    it "Hazardous + Reefer 両方違反は理由を全件返す" $ do
      let r = evaluate (RouteConstraint True True False) (inputFor ["JPTYO", "HKHKG"] ["V001", "V002"])
      ceAccepted r `shouldBe` False
      length (ceReasons r) `shouldBe` 2
      ceReasons r `shouldContain` [HazardousPortViolation "HKHKG"]
      ceReasons r `shouldContain` [ReeferUnavailable "V002"]

  describe "rcDirectPreferred は本サービスでは評価しない" $
    it "directPreferred のみ True でも採用される" $ do
      let r = evaluate (RouteConstraint False False True) (inputFor ["JPTYO", "USNYC"] ["V001"])
      ceAccepted r `shouldBe` True
