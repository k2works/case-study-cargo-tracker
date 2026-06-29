{-# LANGUAGE OverloadedStrings #-}

{- | 輸送見積作成フォーム + 詳細表示 (U-01 / US01, IT3)

H-04 反映: IT2 で API のみ実装されていた見積機能に SSR UI を追加する。
営業担当者が `/estimates/new` でフォーム入力 → POST `/estimates` で見積生成
(候補はスタブ実装) → 303 で `/estimates/:estimateId` に遷移 → 「この見積で
予約する」リンクで `/bookings/new?estimateId=...` へ渡す。
-}
module Cargotracker.Estimation.Views.EstimateFormView
  ( estimateFormPage,
    estimateShowPage,
    estimateListPage,
    estimateNotFoundPage,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (defaultTimeLocale, formatTime)
import Lucid

import Cargotracker.Estimation.Domain.Model.Estimate (Estimate (..))
import qualified Cargotracker.Estimation.Domain.Model.RouteCandidate as RC
import Cargotracker.Estimation.Domain.Model.Value.EstimateId (unEstimateId)
import Cargotracker.Estimation.Domain.Model.Value.EstimateStatus (estimateStatusToText)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

estimateFormPage :: Maybe Text -> Html ()
estimateFormPage mError = pageLayout "輸送見積作成 - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "輸送見積作成 (US01)"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      form_ [action_ "/estimates", method_ "post"] $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "shipperId", class_ "form-label"] "荷主 ID (SHP-XXXXXX)"
          input_
            [ type_ "text"
            , id_ "shipperId"
            , name_ "shipperId"
            , class_ "form-control"
            , required_ "required"
            , maxlength_ "20"
            , placeholder_ "SHP-X1Y2Z3"
            ]
        div_ [class_ "row"] $ do
          div_ [class_ "col-md-6 mb-3"] $ do
            label_ [for_ "origin", class_ "form-label"] "出発地 (UnLocode)"
            input_
              [ type_ "text"
              , id_ "origin"
              , name_ "origin"
              , class_ "form-control"
              , required_ "required"
              , maxlength_ "5"
              , placeholder_ "JPTYO"
              ]
          div_ [class_ "col-md-6 mb-3"] $ do
            label_ [for_ "destination", class_ "form-label"] "目的地 (UnLocode)"
            input_
              [ type_ "text"
              , id_ "destination"
              , name_ "destination"
              , class_ "form-control"
              , required_ "required"
              , maxlength_ "5"
              , placeholder_ "USNYC"
              ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "deadline", class_ "form-label"] "希望到着期限"
          input_
            [ type_ "datetime-local"
            , id_ "deadline"
            , name_ "deadline"
            , class_ "form-control"
            , required_ "required"
            ]
        div_ [class_ "row"] $ do
          div_ [class_ "col-md-6 mb-3"] $ do
            label_ [for_ "cargoType", class_ "form-label"] "貨物種別"
            select_ [id_ "cargoType", name_ "cargoType", class_ "form-select"] $ do
              option_ [value_ "General"] "一般貨物"
              option_ [value_ "Hazardous"] "危険物"
              option_ [value_ "Refrigerated"] "冷凍貨物"
          div_ [class_ "col-md-6 mb-3"] $ do
            label_ [for_ "weight", class_ "form-label"] "重量 (kg)"
            input_
              [ type_ "number"
              , id_ "weight"
              , name_ "weight"
              , class_ "form-control"
              , required_ "required"
              , min_ "0.1"
              , step_ "0.1"
              , placeholder_ "1000.0"
              ]
        button_ [type_ "submit", class_ "btn btn-primary"] "見積を作成"

estimateShowPage :: Estimate -> Html ()
estimateShowPage est = pageLayout "見積詳細 - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      let eid = unEstimateId (estimateId est)
      h1_ [class_ "h3 mb-4"] (toHtml ("見積詳細: " <> eid))
      flashAlert FlashSuccess "見積を作成しました"
      table_ [class_ "table"] $ tbody_ $ do
        tr_ $ do
          th_ "見積 ID"
          td_ (toHtml eid)
        tr_ $ do
          th_ "荷主 ID"
          td_ (toHtml (shipperIdText est))
        tr_ $ do
          th_ "出発地 → 目的地"
          td_
            ( toHtml
                ( unUnLocode (origin est)
                    <> " → "
                    <> unUnLocode (destination est)
                )
            )
        tr_ $ do
          th_ "貨物種別"
          td_ (toHtml (cargoTypeText est))
        tr_ $ do
          th_ "重量 (kg)"
          td_ (toHtml (T.pack (show (weightKg est))))

      h2_ [class_ "h5 mt-4 mb-3"] "経路候補"
      if null (routeCandidates est)
        then div_ [class_ "alert alert-warning"] "該当する経路候補がありません"
        else table_ [class_ "table table-sm table-striped"] $ do
          thead_ $
            tr_ $ do
              th_ "順位"
              th_ "所要日数"
              th_ "予想費用"
              th_ "航海番号"
          tbody_ $ mapM_ candidateRow (routeCandidates est)

      div_ [class_ "mt-4"] $ do
        a_
          [ href_ ("/bookings/new?estimateId=" <> eid)
          , class_ "btn btn-primary me-2"
          ]
          "この見積で予約する"
        a_ [href_ "/estimates/new", class_ "btn btn-secondary me-2"] "別の見積を作成"
        a_ [href_ "/", class_ "btn btn-light"] "トップへ"

candidateRow :: RC.RouteCandidate -> Html ()
candidateRow rc =
  tr_ $ do
    td_ (toHtml (T.pack (show (RC.rank rc))))
    td_ (toHtml (T.pack (show (RC.transitDays rc)) <> " 日"))
    td_ (toHtml ("¥ " <> T.pack (show (RC.estimatedCost rc))))
    td_ (toHtml (T.intercalate ", " (RC.voyageNumbers rc)))

{- | 見積一覧 (US01, IT3 追加導線)。最大 100 件 (Postgres 側で LIMIT 100)。
IT4 で ADR-0006 のページネーション API に移行予定。
-}
estimateListPage :: [Estimate] -> Html ()
estimateListPage ests = pageLayout "見積一覧 - Cargo Tracker" $ do
  div_ [class_ "d-flex justify-content-between align-items-center mb-4"] $ do
    h1_ [class_ "h3 mb-0"] "見積一覧"
    a_ [href_ "/estimates/new", class_ "btn btn-primary"] "新規見積作成"
  let n = length ests
  p_
    [class_ "text-muted small mb-3"]
    ( toHtml
        ("表示中 " <> T.pack (show n) <> " 件 (最大 100 件表示)")
    )
  if n >= 100
    then
      flashAlert
        FlashWarning
        "上限件数に達しています。検索・ページング機能の追加は IT4 で実装予定です。"
    else mempty
  if null ests
    then p_ [class_ "text-muted"] "見積がまだありません。"
    else table_ [class_ "table table-striped"] $ do
      thead_ $ tr_ $ do
        th_ "見積 ID"
        th_ "荷主 ID"
        th_ "出発港"
        th_ "到着港"
        th_ "到着期限"
        th_ "貨物種別"
        th_ "重量 (kg)"
        th_ "状態"
        th_ "候補数"
        th_ ""
      tbody_ (mapM_ row ests)
  where
    row :: Estimate -> Html ()
    row e =
      let eid = unEstimateId (estimateId e)
          UnLocode o = origin e
          UnLocode d = destination e
       in tr_ $ do
            td_ (toHtml (T.take 8 eid <> "…"))
            td_ (toHtml (shipperIdText e))
            td_ (toHtml o)
            td_ (toHtml d)
            td_ (toHtml (T.pack (formatTime defaultTimeLocale "%Y-%m-%d %H:%M" (deadline e))))
            td_ (toHtml (cargoTypeText e))
            td_ (toHtml (T.pack (show (weightKg e))))
            td_ (toHtml (estimateStatusToText (estimateStatus e)))
            td_ (toHtml (T.pack (show (length (routeCandidates e)))))
            td_ $
              a_
                [ href_ ("/estimates/" <> eid)
                , class_ "btn btn-sm btn-outline-primary"
                ]
                "詳細"

estimateNotFoundPage :: Html ()
estimateNotFoundPage = pageLayout "Not Found - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "見積が見つかりません"
      flashAlert FlashDanger "指定された見積 ID は存在しません"
      a_ [href_ "/estimates/new", class_ "btn btn-primary"] "新しい見積を作成"
