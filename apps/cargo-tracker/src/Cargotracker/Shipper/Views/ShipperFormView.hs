{-# LANGUAGE OverloadedStrings #-}

{- | 荷主登録フォームのビュー (IT1 US02/03)

US02 個人 / US03 法人を 1 つのフォームで扱い、kind ラジオで切替える。
htmx を使えば法人フィールドの動的表示が綺麗だが IT1 は素 HTML 入力にする。
-}
module Cargotracker.Shipper.Views.ShipperFormView
  ( shipperFormPage,
    shipperResultPage,
  ) where

import Data.Text (Text)
import Lucid

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

shipperFormPage :: Maybe Text -> Html ()
shipperFormPage mError = pageLayout "荷主登録 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "荷主登録"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      form_ [action_ "/shippers/new", method_ "post"] $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "shipperId", class_ "form-label"] "荷主 ID (SHP-XXXXXX)"
          input_
            [ type_ "text"
            , id_ "shipperId"
            , name_ "shipperId"
            , class_ "form-control"
            , required_ "required"
            , pattern_ "SHP-[A-Z0-9]{6}"
            , placeholder_ "SHP-A1B2C3"
            ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "name", class_ "form-label"] "氏名 / 社名 (255 文字以内)"
          input_
            [ type_ "text"
            , id_ "name"
            , name_ "name"
            , class_ "form-control"
            , required_ "required"
            , maxlength_ "255"
            , placeholder_ "山田太郎 / 株式会社あいうえお"
            ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "email", class_ "form-label"] "連絡先メール"
          input_
            [ type_ "email"
            , id_ "email"
            , name_ "email"
            , class_ "form-control"
            , required_ "required"
            ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "address", class_ "form-label"] "住所 (500 文字以内)"
          textarea_
            [ id_ "address"
            , name_ "address"
            , class_ "form-control"
            , required_ "required"
            , rows_ "3"
            ]
            mempty
        fieldset_ [class_ "mb-3"] $ do
          legend_ [class_ "col-form-label"] "種別"
          div_ [class_ "form-check"] $ do
            input_
              [ type_ "radio"
              , id_ "kind-individual"
              , name_ "kind"
              , value_ "individual"
              , class_ "form-check-input"
              , checked_
              ]
            label_ [for_ "kind-individual", class_ "form-check-label"] "個人 (US02)"
          div_ [class_ "form-check"] $ do
            input_
              [ type_ "radio"
              , id_ "kind-corporate"
              , name_ "kind"
              , value_ "corporate"
              , class_ "form-check-input"
              ]
            label_ [for_ "kind-corporate", class_ "form-check-label"] "法人 (US03)"
        div_ [class_ "mb-3"] $ do
          label_ [for_ "corporateNumber", class_ "form-label"] "法人番号 (法人時のみ、13 桁)"
          input_
            [ type_ "text"
            , id_ "corporateNumber"
            , name_ "corporateNumber"
            , class_ "form-control"
            , pattern_ "[0-9]{13}"
            , placeholder_ "1234567890123"
            ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "contractRank", class_ "form-label"] "契約ランク (法人時のみ)"
          select_ [id_ "contractRank", name_ "contractRank", class_ "form-select"] $ do
            option_ [value_ ""] "(選択)"
            option_ [value_ "Bronze"] "Bronze"
            option_ [value_ "Silver"] "Silver"
            option_ [value_ "Gold"] "Gold"
        button_ [type_ "submit", class_ "btn btn-primary"] "登録"

shipperResultPage :: Bool -> Text -> Html ()
shipperResultPage success message = pageLayout "登録結果 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "登録結果"
      flashAlert (if success then FlashSuccess else FlashDanger) message
      a_ [href_ "/shippers/new", class_ "btn btn-secondary me-2"] "もう 1 件登録"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"
