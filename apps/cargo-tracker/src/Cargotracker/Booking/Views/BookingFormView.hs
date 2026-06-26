{-# LANGUAGE OverloadedStrings #-}

{- | 貨物予約登録フォームのビュー (IT1 US04)

origin/destination は location マスタの 10 港から選択。
deadline は datetime-local 入力 (ISO 8601 への変換は handler で実施)。
-}
module Cargotracker.Booking.Views.BookingFormView
  ( bookingFormPage,
    bookingResultPage,
  ) where

import Data.Text (Text)
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

-- IT1 デモ用の港リスト (location テーブルのシードと一致)
ports :: [(Text, Text)]
ports =
  [ ("JPTYO", "JPTYO - Tokyo")
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

portSelect :: Text -> Text -> Text -> Html ()
portSelect inputId nameAttr label = do
  label_ [for_ inputId, class_ "form-label"] (toHtml label)
  select_ [id_ inputId, name_ nameAttr, class_ "form-select", required_ "required"] $
    mapM_ (\(code, lbl) -> option_ [value_ code] (toHtml lbl)) ports

bookingFormPage :: Maybe Text -> Html ()
bookingFormPage mError = pageLayout "貨物予約登録 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "貨物予約登録 (US04)"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      form_ [action_ "/bookings/new", method_ "post"] $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "bookingId", class_ "form-label"] "予約 ID (BK-XXXXXX)"
          input_
            [ type_ "text"
            , id_ "bookingId"
            , name_ "bookingId"
            , class_ "form-control"
            , required_ "required"
            , pattern_ "BK-[A-Z0-9]{6}"
            , placeholder_ "BK-A1B2C3"
            ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "shipperId", class_ "form-label"] "荷主 ID (SHP-XXXXXX、要事前登録)"
          input_
            [ type_ "text"
            , id_ "shipperId"
            , name_ "shipperId"
            , class_ "form-control"
            , required_ "required"
            , pattern_ "SHP-[A-Z0-9]{6}"
            , placeholder_ "SHP-A1B2C3 / 検索キーワード入力で候補表示"
            , makeAttribute "hx-get" "/shippers/search"
            , makeAttribute "hx-trigger" "keyup changed delay:300ms"
            , makeAttribute "hx-target" "#shipper-results"
            , makeAttribute "hx-swap" "innerHTML"
            ]
          div_ [id_ "shipper-results", class_ "list-group mt-1"] mempty
        div_ [class_ "row"] $ do
          div_ [class_ "col-md-6 mb-3"] (portSelect "origin" "origin" "出発港")
          div_ [class_ "col-md-6 mb-3"] (portSelect "destination" "destination" "到着港")
        div_ [class_ "mb-3"] $ do
          label_ [for_ "deadline", class_ "form-label"] "到着期限"
          input_
            [ type_ "datetime-local"
            , id_ "deadline"
            , name_ "deadline"
            , class_ "form-control"
            , required_ "required"
            ]
        button_ [type_ "submit", class_ "btn btn-primary"] "予約"

bookingResultPage :: Bool -> Text -> Html ()
bookingResultPage success message = pageLayout "予約結果 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "予約結果"
      flashAlert (if success then FlashSuccess else FlashDanger) message
      a_ [href_ "/bookings/new", class_ "btn btn-secondary me-2"] "もう 1 件予約"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"
