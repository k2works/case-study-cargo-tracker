{-# LANGUAGE OverloadedStrings #-}

{- | 貨物予約登録フォームのビュー (IT1 US04)

origin/destination は location マスタの 10 港から選択。
deadline は datetime-local 入力 (ISO 8601 への変換は handler で実施)。
-}
module Cargotracker.Booking.Views.BookingFormView
  ( bookingFormPage,
    bookingResultPage,
    cargoTypeRowFragment,
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
        -- T-07 (IT2): 予約 ID はサーバ側で自動採番。手入力フィールドは廃止する。
        -- M-03 (IT3): BookingFormRequest.bookingId を Maybe Text 化したため
        -- hidden の死コードは不要 (削除済)。
        p_
          [class_ "text-muted small mb-3"]
          "予約 ID (BK-XXXXXX) は登録時に自動採番されます。"
        div_ [class_ "mb-3"] $ do
          label_ [for_ "shipperId", class_ "form-label"] "荷主 ID (検索で選択)"
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
        -- U-02 (IT3, H-05): 貨物種別 select + htmx 動的フィールド差替え。
        -- General は追加入力なし、Hazardous は危険物 3 項目、Refrigerated は
        -- 温度範囲 + 単位を要求する。サーバ側で hx-get で fragment を返す。
        div_ [class_ "mb-3"] $ do
          label_ [for_ "cargoType", class_ "form-label"] "貨物種別"
          select_
            [ id_ "cargoType"
            , name_ "cargoType"
            , class_ "form-select"
            , required_ "required"
            , makeAttribute "hx-get" "/bookings/new/cargo-type-row"
            , makeAttribute "hx-trigger" "change"
            , makeAttribute "hx-target" "#cargo-fields"
            , makeAttribute "hx-swap" "innerHTML"
            , makeAttribute "hx-include" "this"
            ]
            $ do
              option_ [value_ "General", selected_ "selected"] "一般貨物"
              option_ [value_ "Hazardous"] "危険物"
              option_ [value_ "Refrigerated"] "冷凍貨物"
        div_ [id_ "cargo-fields"] mempty
        button_ [type_ "submit", class_ "btn btn-primary"] "予約"

{- | U-02 (IT3): /bookings/new/cargo-type-row が返す htmx fragment。

cargoType の値に応じて追加入力フィールド (危険物クラス / UN 番号 / 正式
輸送品名、冷凍温度範囲 / 単位) を差し替え表示する。General は空。
-}
cargoTypeRowFragment :: Text -> Html ()
cargoTypeRowFragment "Hazardous" = hazardousFields
cargoTypeRowFragment "Refrigerated" = refrigeratedFields
cargoTypeRowFragment _ = mempty

hazardousFields :: Html ()
hazardousFields = div_ [class_ "border rounded p-3 bg-light"] $ do
  h6_ [class_ "mb-3"] "危険物詳細"
  div_ [class_ "mb-3"] $ do
    label_ [for_ "hazardousClass", class_ "form-label"] "危険物クラス"
    input_
      [ type_ "text"
      , id_ "hazardousClass"
      , name_ "hazardousClass"
      , class_ "form-control"
      , required_ "required"
      , maxlength_ "10"
      , placeholder_ "例: 3 (引火性液体)"
      ]
  div_ [class_ "mb-3"] $ do
    label_ [for_ "unNumber", class_ "form-label"] "UN 番号 (4 桁数字)"
    input_
      [ type_ "text"
      , id_ "unNumber"
      , name_ "unNumber"
      , class_ "form-control"
      , required_ "required"
      , pattern_ "[0-9]{4}"
      , maxlength_ "4"
      , placeholder_ "1203"
      ]
  div_ [class_ "mb-3"] $ do
    label_ [for_ "properShippingName", class_ "form-label"] "正式輸送品名"
    input_
      [ type_ "text"
      , id_ "properShippingName"
      , name_ "properShippingName"
      , class_ "form-control"
      , required_ "required"
      , maxlength_ "200"
      , placeholder_ "GASOLINE"
      ]

refrigeratedFields :: Html ()
refrigeratedFields = div_ [class_ "border rounded p-3 bg-light"] $ do
  h6_ [class_ "mb-3"] "冷凍管理"
  div_ [class_ "row"] $ do
    div_ [class_ "col-md-4 mb-3"] $ do
      label_ [for_ "minTemperature", class_ "form-label"] "最低温度"
      input_
        [ type_ "number"
        , id_ "minTemperature"
        , name_ "minTemperature"
        , class_ "form-control"
        , required_ "required"
        , step_ "0.1"
        , placeholder_ "-20"
        ]
    div_ [class_ "col-md-4 mb-3"] $ do
      label_ [for_ "maxTemperature", class_ "form-label"] "最高温度"
      input_
        [ type_ "number"
        , id_ "maxTemperature"
        , name_ "maxTemperature"
        , class_ "form-control"
        , required_ "required"
        , step_ "0.1"
        , placeholder_ "-15"
        ]
    div_ [class_ "col-md-4 mb-3"] $ do
      label_ [for_ "temperatureUnit", class_ "form-label"] "単位"
      select_
        [ id_ "temperatureUnit"
        , name_ "temperatureUnit"
        , class_ "form-select"
        , required_ "required"
        ]
        $ do
          option_ [value_ "Celsius", selected_ "selected"] "摂氏 (°C)"
          option_ [value_ "Fahrenheit"] "華氏 (°F)"

bookingResultPage :: Bool -> Text -> Html ()
bookingResultPage success message = pageLayout "予約結果 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "予約結果"
      flashAlert (if success then FlashSuccess else FlashDanger) message
      a_ [href_ "/bookings/new", class_ "btn btn-secondary me-2"] "もう 1 件予約"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"
