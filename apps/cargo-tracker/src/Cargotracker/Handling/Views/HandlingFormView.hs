{-# LANGUAGE OverloadedStrings #-}

-- | 荷役登録フォーム ビュー (US15, IT5)
module Cargotracker.Handling.Views.HandlingFormView
  ( handlingFormPage,
  ) where

import Data.Text (Text)
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

{- | 荷役登録フォーム。IT5 段階では最小構成 (Booking 選択・Location 選択は Text 入力)。
IT6 で Booking dropdown / Voyage dropdown + Claim 時の確認コード入力欄追加。
-}
handlingFormPage :: Maybe Text -> Html ()
handlingFormPage mFlash = pageLayout "荷役登録 - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "荷役イベント登録"
      case mFlash of
        Just "invalid-state" -> flashAlert FlashDanger "入力内容にエラーがあります"
        Just "not-found" -> flashAlert FlashDanger "指定された予約が見つかりません"
        Just "success" -> flashAlert FlashSuccess "荷役イベントを登録しました"
        _ -> mempty
      form_
        [ action_ "/handling/new"
        , method_ "post"
        , class_ "row g-3"
        , makeAttribute "data-story" "US15"
        ]
        $ do
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "bookingId", class_ "form-label"] "予約 ID"
            input_
              [ type_ "text"
              , id_ "bookingId"
              , name_ "bookingId"
              , class_ "form-control"
              , placeholder_ "BK-A1B2C3"
              , required_ ""
              ]
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "eventType", class_ "form-label"] "種別"
            select_
              [ id_ "eventType"
              , name_ "eventType"
              , class_ "form-select"
              , required_ ""
              ]
              $ do
                option_ [value_ "RECEIVE"] "受領 (RECEIVE)"
                option_ [value_ "LOAD"] "積込 (LOAD)"
                option_ [value_ "UNLOAD"] "荷降し (UNLOAD)"
                option_ [value_ "CUSTOMS"] "通関 (CUSTOMS)"
                option_ [value_ "CLAIM"] "引取 (CLAIM)"
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "completionTime", class_ "form-label"] "実施時刻"
            input_
              [ type_ "datetime-local"
              , id_ "completionTime"
              , name_ "completionTime"
              , class_ "form-control"
              , required_ ""
              ]
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "locationUnlocode", class_ "form-label"] "場所 (UN/LOCODE)"
            input_
              [ type_ "text"
              , id_ "locationUnlocode"
              , name_ "locationUnlocode"
              , class_ "form-control"
              , placeholder_ "JPTYO"
              , makeAttribute "pattern" "[A-Z]{2}[A-Z0-9]{3}"
              , required_ ""
              ]
          div_ [class_ "col-md-6"] $ do
            label_ [for_ "voyageNumber", class_ "form-label"] "航海番号 (LOAD/UNLOAD 時必須)"
            input_
              [ type_ "text"
              , id_ "voyageNumber"
              , name_ "voyageNumber"
              , class_ "form-control"
              , placeholder_ "V0001"
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
            button_ [type_ "submit", class_ "btn btn-primary me-2"] "登録する"
            a_ [href_ "/", class_ "btn btn-secondary"] "キャンセル"
