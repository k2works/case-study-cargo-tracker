{-# LANGUAGE OverloadedStrings #-}

-- | 引取確認フォーム ビュー (US16, IT5)
module Cargotracker.Handling.Views.ClaimFormView
  ( claimFormPage,
  ) where

import Data.Text (Text)
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

claimFormPage :: Maybe Text -> Html ()
claimFormPage mFlash = pageLayout "引取確認 - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-6"] $ do
      h1_ [class_ "h3 mb-4"] "引取確認"
      case mFlash of
        Just "success" ->
          flashAlert FlashSuccess "引取を確認しました。ご利用ありがとうございました。"
        Just "code-mismatch" ->
          flashAlert FlashDanger "確認コードが正しくありません。もう一度お試しください。"
        Just "code-used" ->
          flashAlert FlashDanger "この確認コードは既に使用されています。"
        Just "code-lock" ->
          flashAlert FlashDanger "試行回数の上限に達しました。荷主にご連絡ください。"
        Just "invalid" ->
          flashAlert FlashDanger "入力内容にエラーがあります。"
        Just "not-found" ->
          flashAlert FlashDanger "指定された予約が見つかりません。"
        _ ->
          p_
            [class_ "text-muted"]
            "荷受人に配信された 6 桁の確認コードを入力してください。"
      form_
        [ action_ "/handling/claim"
        , method_ "post"
        , class_ "row g-3"
        , makeAttribute "data-story" "US16"
        ]
        $ do
          div_ [class_ "col-md-12"] $ do
            label_ [for_ "bookingId", class_ "form-label"] "予約 ID"
            input_
              [ type_ "text"
              , id_ "bookingId"
              , name_ "bookingId"
              , class_ "form-control"
              , placeholder_ "BK-A1B2C3"
              , required_ ""
              ]
          div_ [class_ "col-md-12"] $ do
            label_ [for_ "confirmationCode", class_ "form-label"] "確認コード (6 桁数字)"
            input_
              [ type_ "text"
              , id_ "confirmationCode"
              , name_ "confirmationCode"
              , class_ "form-control form-control-lg text-center"
              , makeAttribute "inputmode" "numeric"
              , makeAttribute "pattern" "[0-9]{6}"
              , placeholder_ "000000"
              , required_ ""
              , makeAttribute "autocomplete" "off"
              ]
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "locationUnlocode", class_ "form-label"] "引取地 (UN/LOCODE)"
            input_
              [ type_ "text"
              , id_ "locationUnlocode"
              , name_ "locationUnlocode"
              , class_ "form-control"
              , makeAttribute "pattern" "[A-Z]{2}[A-Z0-9]{3}"
              , required_ ""
              ]
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "operatorName", class_ "form-label"] "作業員名"
            input_
              [ type_ "text"
              , id_ "operatorName"
              , name_ "operatorName"
              , class_ "form-control"
              , required_ ""
              ]
          div_ [class_ "col-12"] $ do
            button_ [type_ "submit", class_ "btn btn-primary me-2"] "引取を確認する"
            a_ [href_ "/", class_ "btn btn-secondary"] "キャンセル"
