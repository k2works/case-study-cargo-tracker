{-# LANGUAGE OverloadedStrings #-}

{- | 航海登録フォームのビュー (IT1 US24)

固定 3 区間の入力行を用意し、空行はハンドラ側でフィルタする。
IT2 で htmx による動的行追加に置き換える予定。
-}
module Cargotracker.Routing.Views.VoyageFormView
  ( voyageFormPage,
    voyageEditPage,
    voyageResultPage,
    movementRow,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

ports :: [(Text, Text)]
ports =
  [ ("", "(未選択)")
  , ("JPTYO", "JPTYO - Tokyo")
  , ("JPOSA", "JPOSA - Osaka")
  , ("JPYOK", "JPYOK - Yokohama")
  , ("USNYC", "USNYC - New York")
  , ("USLAX", "USLAX - Los Angeles")
  , ("USSEA", "USSEA - Seattle")
  , ("CNSHA", "CNSHA - Shanghai")
  , ("HKHKG", "HKHKG - Hong Kong")
  , ("SGSIN", "SGSIN - Singapore")
  , ("GBLON", "GBLON - London")
  ]

portSelect :: Text -> Text -> Bool -> Html ()
portSelect nameAttr label required = do
  label_ [class_ "form-label small"] (toHtml label)
  select_ ([name_ nameAttr, class_ "form-select form-select-sm"] <> [required_ "required" | required]) $
    mapM_ (\(code, lbl) -> option_ [value_ code] (toHtml lbl)) ports

movementRow :: Int -> Bool -> Html ()
movementRow i isFirst = do
  let n = T.pack (show i)
  h6_ [class_ "mt-3"] (toHtml ("区間 " <> n <> if isFirst then " (必須)" else " (任意)"))
  div_ [class_ "row g-2"] $ do
    div_ [class_ "col-md-3"] $
      portSelect ("movement" <> n <> "Departure") "出発港" isFirst
    div_ [class_ "col-md-3"] $
      portSelect ("movement" <> n <> "Arrival") "到着港" isFirst
    div_ [class_ "col-md-3"] $ do
      label_ [class_ "form-label small"] "出発時刻"
      input_ $
        [ type_ "datetime-local"
        , name_ ("movement" <> n <> "DepartureTime")
        , class_ "form-control form-control-sm"
        ]
          <> [required_ "required" | isFirst]
    div_ [class_ "col-md-3"] $ do
      label_ [class_ "form-label small"] "到着時刻"
      input_ $
        [ type_ "datetime-local"
        , name_ ("movement" <> n <> "ArrivalTime")
        , class_ "form-control form-control-sm"
        ]
          <> [required_ "required" | isFirst]

voyageFormPage :: Maybe Text -> Html ()
voyageFormPage mError = pageLayout "航海登録 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      h1_ [class_ "h3 mb-4"] "航海スケジュール登録 (US24)"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      form_ [action_ "/voyages/new", method_ "post"] $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "voyageNumber", class_ "form-label"] "航海番号 (1-20 文字)"
          input_
            [ type_ "text"
            , id_ "voyageNumber"
            , name_ "voyageNumber"
            , class_ "form-control"
            , required_ "required"
            , maxlength_ "20"
            , placeholder_ "V0001"
            ]
        p_
          [class_ "text-muted small"]
          "区間 1 は必須、2-3 は任意。連続性 (前区間の到着港 = 次区間の出発港) は登録時に検証されます。"
        div_ [id_ "movements-container"] $ do
          movementRow 1 True
          movementRow 2 False
          movementRow 3 False
        div_ [class_ "mt-2"] $
          button_
            [ type_ "button"
            , class_ "btn btn-sm btn-outline-secondary"
            , makeAttribute "hx-get" "/voyages/new/movement-row"
            , makeAttribute "hx-target" "#movements-container"
            , makeAttribute "hx-swap" "beforeend"
            ]
            "+ 区間を追加"
        button_ [type_ "submit", class_ "btn btn-primary mt-4"] "登録"

-- US25 (IT2): 既存航海の更新フォーム。`voyageFormPage` と同じ入力構造を
-- 流用し、action だけ `/voyages/:voyageNumber/update` に切り替える。
-- 既存値のプリフィル (input value 属性への埋め込み) は IT3 で対応する。
voyageEditPage :: Text -> Maybe Text -> Html ()
voyageEditPage vn mError = pageLayout "航海更新 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      h1_ [class_ "h3 mb-4"] (toHtml ("航海スケジュール更新 (US25): " <> vn))
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      flashAlert
        FlashWarning
        "既存の区間を全て上書きします。変更不要な区間も再度入力してください (プリフィルは IT3 対応予定)。"
      form_
        [action_ ("/voyages/" <> vn <> "/update"), method_ "post"]
        $ do
          input_ [type_ "hidden", name_ "voyageNumber", value_ vn]
          p_
            [class_ "text-muted small"]
            "区間 1 は必須、2-3 は任意。連続性 (前区間の到着港 = 次区間の出発港) は更新時に検証されます。"
          div_ [id_ "movements-container"] $ do
            movementRow 1 True
            movementRow 2 False
            movementRow 3 False
          div_ [class_ "mt-2"] $
            button_
              [ type_ "button"
              , class_ "btn btn-sm btn-outline-secondary"
              , makeAttribute "hx-get" "/voyages/new/movement-row"
              , makeAttribute "hx-target" "#movements-container"
              , makeAttribute "hx-swap" "beforeend"
              ]
              "+ 区間を追加"
          div_ [class_ "mt-4"] $ do
            button_ [type_ "submit", class_ "btn btn-primary me-2"] "更新する"
            a_
              [href_ ("/voyages/" <> vn), class_ "btn btn-light"]
              "キャンセル"

voyageResultPage :: Bool -> Text -> Html ()
voyageResultPage success message = pageLayout "航海結果 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "航海登録結果"
      flashAlert (if success then FlashSuccess else FlashDanger) message
      a_ [href_ "/voyages/new", class_ "btn btn-secondary me-2"] "もう 1 件登録"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"
