-- | RouteEvaluationView のテスト (US08b, IT4)
module Estimation.Views.RouteEvaluationViewSpec (spec) where

import qualified Data.Text as T
import Data.Text.Lazy (toStrict)
import Lucid (Html, renderText)
import Test.Hspec

import Cargotracker.Estimation.Application.EvaluateRouteCandidatesCommand
  ( EvaluatedRoute (..),
    RouteCandidateInput (..),
  )
import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation (..),
    ExclusionReason (..),
  )
import Cargotracker.Estimation.Views.RouteEvaluationView
  ( constraintFormFragment,
    evaluationFragment,
  )

render :: Html () -> T.Text
render = toStrict . renderText

acceptedRoute :: EvaluatedRoute
acceptedRoute =
  EvaluatedRoute
    { evCandidate = RouteCandidateInput 0 ["JPTYO", "USNYC"] ["V001"]
    , evEvaluation = ConstraintEvaluation True []
    }

rejectedHazardousRoute :: EvaluatedRoute
rejectedHazardousRoute =
  EvaluatedRoute
    { evCandidate = RouteCandidateInput 2 ["JPTYO", "HKHKG", "USNYC"] ["V001"]
    , evEvaluation = ConstraintEvaluation False [HazardousPortViolation "HKHKG"]
    }

rejectedReeferRoute :: EvaluatedRoute
rejectedReeferRoute =
  EvaluatedRoute
    { evCandidate = RouteCandidateInput 1 ["JPTYO", "USNYC"] ["V001", "V002"]
    , evEvaluation = ConstraintEvaluation False [ReeferUnavailable "V002"]
    }

spec :: Spec
spec = describe "RouteEvaluationView (US08b / IT4)" $ do
  describe "evaluationFragment" $ do
    it "全候補が accepted の場合は採用候補表のみ表示 (除外セクションなし)" $ do
      let html = render (evaluationFragment [acceptedRoute])
      html `shouldSatisfy` T.isInfixOf "採用候補"
      html `shouldSatisfy` T.isInfixOf "JPTYO → USNYC"
      html `shouldSatisfy` (not . T.isInfixOf "除外された候補")

    it "accepted が空なら alert-warning で「全候補除外」を表示" $ do
      let html = render (evaluationFragment [rejectedHazardousRoute])
      html `shouldSatisfy` T.isInfixOf "alert-warning"
      html `shouldSatisfy` T.isInfixOf "制約により全候補が除外されました"

    it "rejected があれば除外理由 (HazardousPortViolation) を表示" $ do
      let html = render (evaluationFragment [acceptedRoute, rejectedHazardousRoute])
      html `shouldSatisfy` T.isInfixOf "除外された候補"
      html `shouldSatisfy` T.isInfixOf "危険物受入不可港を含む: HKHKG"

    it "rejected の ReeferUnavailable も日本語ラベルで表示" $ do
      let html = render (evaluationFragment [acceptedRoute, rejectedReeferRoute])
      html `shouldSatisfy` T.isInfixOf "冷凍非対応の航海を含む: V002"

    it "accepted の rank を td に出力" $ do
      let html = render (evaluationFragment [acceptedRoute])
      html `shouldSatisfy` T.isInfixOf ">0</td>"

    it "id=\"evaluation-result\" を持つ (htmx hx-swap=outerHTML 対象)" $ do
      let html = render (evaluationFragment [])
      html `shouldSatisfy` T.isInfixOf "id=\"evaluation-result\""

  describe "constraintFormFragment" $ do
    it "POST /bookings/:id/routes/evaluate を hx-post とする" $ do
      let html = render (constraintFormFragment "BK-A1B2C3")
      html `shouldSatisfy` T.isInfixOf "hx-post=\"/bookings/BK-A1B2C3/routes/evaluate\""

    it "3 つの制約チェックボックス (hazardous/reefer/direct) を含む" $ do
      let html = render (constraintFormFragment "BK-A1B2C3")
      html `shouldSatisfy` T.isInfixOf "name=\"hazardous\""
      html `shouldSatisfy` T.isInfixOf "name=\"reefer\""
      html `shouldSatisfy` T.isInfixOf "name=\"direct\""

    it "「制約を再評価」ボタンを含む" $ do
      let html = render (constraintFormFragment "BK-A1B2C3")
      html `shouldSatisfy` T.isInfixOf "制約を再評価"
