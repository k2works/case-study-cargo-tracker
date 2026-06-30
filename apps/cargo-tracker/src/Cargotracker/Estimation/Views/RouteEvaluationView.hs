{-# LANGUAGE OverloadedStrings #-}

{- | 経路候補制約評価結果ビュー (US08b, IT4)

`/bookings/:bookingId/routes/evaluate` の結果を表示する htmx fragment。
EvaluatedRoute (経路 + 評価結果) を受け取り、accepted と rejected を別セクションで
描画する。除外理由は HazardousPortViolation / ReeferUnavailable の業務的な日本語に
変換する。
-}
module Cargotracker.Estimation.Views.RouteEvaluationView
  ( evaluationFragment,
    constraintFormFragment,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Estimation.Application.EvaluateRouteCandidatesCommand
  ( EvaluatedRoute (..),
    RouteCandidateInput (..),
  )
import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation (..),
    ExclusionReason (..),
  )

-- | 制約評価結果を accepted / rejected に分割して描画する htmx fragment
evaluationFragment :: [EvaluatedRoute] -> Html ()
evaluationFragment evaluated =
  let accepted = [e | e <- evaluated, ceAccepted (evEvaluation e)]
      rejected = [e | e <- evaluated, not (ceAccepted (evEvaluation e))]
   in div_ [id_ "evaluation-result"] $ do
        acceptedSection accepted
        if null rejected
          then mempty
          else rejectedSection rejected

acceptedSection :: [EvaluatedRoute] -> Html ()
acceptedSection [] =
  div_ [class_ "alert alert-warning"] "制約により全候補が除外されました。条件を見直してください。"
acceptedSection routes = do
  h3_ [class_ "h5 mt-3"] "採用候補"
  table_ [class_ "table table-sm table-striped"] $ do
    thead_ $ tr_ $ do
      th_ "rank"
      th_ "寄港港"
      th_ "航海番号"
    tbody_ $ mapM_ acceptedRow routes

acceptedRow :: EvaluatedRoute -> Html ()
acceptedRow EvaluatedRoute {evCandidate = c} = tr_ $ do
  td_ (toHtml (T.pack (show (rciRank c))))
  td_ (toHtml (T.intercalate " → " (rciPorts c)))
  td_ (toHtml (T.intercalate ", " (rciVoyages c)))

rejectedSection :: [EvaluatedRoute] -> Html ()
rejectedSection routes = do
  h3_ [class_ "h6 mt-4 text-muted"] "除外された候補"
  ul_ [class_ "list-group"] $ mapM_ rejectedItem routes

rejectedItem :: EvaluatedRoute -> Html ()
rejectedItem EvaluatedRoute {evCandidate = c, evEvaluation = ev} =
  li_ [class_ "list-group-item list-group-item-warning"] $ do
    strong_ (toHtml ("rank " <> T.pack (show (rciRank c)) <> ": "))
    span_ (toHtml (T.intercalate " → " (rciPorts c)))
    div_ [class_ "small text-muted mt-1"] $
      ul_ [class_ "mb-0"] $
        mapM_ (li_ . toHtml . reasonLabel) (ceReasons ev)

reasonLabel :: ExclusionReason -> Text
reasonLabel (HazardousPortViolation port) = "危険物受入不可港を含む: " <> port
reasonLabel (ReeferUnavailable voy) = "冷凍非対応の航海を含む: " <> voy

-- | 制約条件入力フォーム (htmx で POST evaluate)
constraintFormFragment :: Text -> Html ()
constraintFormFragment bid =
  form_
    [ makeAttribute "hx-post" ("/bookings/" <> bid <> "/routes/evaluate")
    , makeAttribute "hx-target" "#evaluation-result"
    , makeAttribute "hx-swap" "outerHTML"
    , class_ "mb-3"
    ]
    $ do
      div_ [class_ "form-check form-check-inline"] $ do
        input_
          [ type_ "checkbox"
          , class_ "form-check-input"
          , id_ "constraint-hazardous"
          , name_ "hazardous"
          ]
        label_ [for_ "constraint-hazardous", class_ "form-check-label"] "危険物港回避"
      div_ [class_ "form-check form-check-inline"] $ do
        input_
          [ type_ "checkbox"
          , class_ "form-check-input"
          , id_ "constraint-reefer"
          , name_ "reefer"
          ]
        label_ [for_ "constraint-reefer", class_ "form-check-label"] "冷凍船指定"
      div_ [class_ "form-check form-check-inline"] $ do
        input_
          [ type_ "checkbox"
          , class_ "form-check-input"
          , id_ "constraint-direct"
          , name_ "direct"
          ]
        label_ [for_ "constraint-direct", class_ "form-check-label"] "直行優先"
      button_
        [type_ "submit", class_ "btn btn-primary btn-sm ms-2"]
        "制約を再評価"
