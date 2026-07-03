{-# LANGUAGE OverloadedStrings #-}

{- | 例外登録フォーム画面 3 種 (US19/US20, IT7)

`/exceptions/{delay,damage,loss}` の GET 経路で返される Lucid View。
POST 送信先は各 URL の POST 経路 (Servant ExceptionListApi で受信)。

ui_design.md §例外一覧・登録画面 のワイヤーフレームに準拠。
-}
module Cargotracker.Exception.Views.ExceptionFormViews
  ( delayFormPage,
    damageFormPage,
    lossFormPage,
    exceptionNotFoundPage,
  ) where

import Data.Text (Text)
import Lucid
import Lucid.Base (makeAttribute)

-- | 全 3 画面で共通のレイアウト骨格
formLayout :: Text -> Html () -> Html ()
formLayout title body = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ (toHtml (title <> " - Cargo Tracker"))
    link_ [rel_ "stylesheet", href_ "/static/css/bootstrap.min.css"]
  body_ $ div_ [class_ "container my-4"] $ do
    h1_ [class_ "mb-3"] (toHtml title)
    p_ [class_ "text-muted"] "登録すると Tracking BC が「例外対応中」状態に遷移します (ADR-0014)。"
    body
    div_ [class_ "mt-3"] $
      a_ [href_ "/exceptions", class_ "btn btn-link"] "← 一覧に戻る"

-- | 共通 hidden フィールド (reporter userId/role は Session から取得予定、暫定的にフォームで受ける)
reporterFields :: Html ()
reporterFields = do
  div_ [class_ "row g-3"] $ do
    div_ [class_ "col-md-6"] $ do
      label_ [class_ "form-label", for_ "reporterUserId"] "報告者 ID"
      input_
        [ type_ "text"
        , name_ "reporterUserId"
        , id_ "reporterUserId"
        , class_ "form-control"
        , required_ ""
        , placeholder_ "user-XX (T6-09 で Session から自動取得予定)"
        ]
    div_ [class_ "col-md-6"] $ do
      label_ [class_ "form-label", for_ "reporterRole"] "役割"
      select_
        [ name_ "reporterRole"
        , id_ "reporterRole"
        , class_ "form-select"
        , required_ ""
        ]
        $ do
          option_ [value_ "Handler"] "Handler"
          option_ [value_ "Tracker"] "Tracker"
          option_ [value_ "Admin"] "Admin"

-- | 重要度セレクタ (4 段階)
severitySelect :: Text -> Html ()
severitySelect defaultVal =
  div_ [class_ "col-md-4"] $ do
    label_ [class_ "form-label", for_ "severity"] "重要度"
    select_
      [ name_ "severity"
      , id_ "severity"
      , class_ "form-select"
      , required_ ""
      ]
      $ do
        option_
          ([value_ "LOW"] <> selectedIf "LOW" defaultVal)
          "Low"
        option_
          ([value_ "MEDIUM"] <> selectedIf "MEDIUM" defaultVal)
          "Medium"
        option_
          ([value_ "HIGH"] <> selectedIf "HIGH" defaultVal)
          "High"
        option_
          ([value_ "CRITICAL"] <> selectedIf "CRITICAL" defaultVal)
          "Critical"
  where
    selectedIf a b = [selected_ "" | a == b]

commonFields :: Html ()
commonFields = do
  div_ [class_ "row g-3"] $ do
    div_ [class_ "col-md-6"] $ do
      label_ [class_ "form-label", for_ "exceptionId"] "例外 ID"
      input_
        [ type_ "text"
        , name_ "exceptionId"
        , id_ "exceptionId"
        , class_ "form-control"
        , required_ ""
        , placeholder_ "EX-XXXXX (UUID 想定)"
        ]
    div_ [class_ "col-md-6"] $ do
      label_ [class_ "form-label", for_ "trackingNumber"] "追跡番号"
      input_
        [ type_ "text"
        , name_ "trackingNumber"
        , id_ "trackingNumber"
        , class_ "form-control"
        , required_ ""
        , placeholder_ "TR-XXXXXXXX"
        , pattern_ "[A-Z0-9]{8}"
        ]

submitCancelButtons :: Html ()
submitCancelButtons = div_ [class_ "mt-4 d-flex gap-2"] $ do
  button_ [type_ "submit", class_ "btn btn-primary"] "登録する"
  a_ [href_ "/exceptions", class_ "btn btn-outline-secondary"] "キャンセル"

delayFormPage :: Html ()
delayFormPage = formLayout "遅延例外を登録"
  $ form_
    [ action_ "/exceptions/delay"
    , method_ "post"
    , makeAttribute "data-testid" "exception-form-delay"
    ]
  $ do
    commonFields
    div_ [class_ "row g-3 mt-2"] $ do
      div_ [class_ "col-md-4"] $ do
        label_ [class_ "form-label", for_ "delayHours"] "遅延時間 (時間)"
        input_
          [ type_ "number"
          , name_ "delayHours"
          , id_ "delayHours"
          , class_ "form-control"
          , min_ "1"
          , required_ ""
          ]
      severitySelect "MEDIUM"
    div_ [class_ "mt-3"] $ do
      label_ [class_ "form-label", for_ "reason"] "遅延理由"
      textarea_
        [ name_ "reason"
        , id_ "reason"
        , class_ "form-control"
        , rows_ "3"
        , required_ ""
        , placeholder_ "500 文字以内"
        ]
        ""
    div_ [class_ "mt-3"] reporterFields
    submitCancelButtons

damageFormPage :: Html ()
damageFormPage = formLayout "破損例外を登録"
  $ form_
    [ action_ "/exceptions/damage"
    , method_ "post"
    , makeAttribute "data-testid" "exception-form-damage"
    ]
  $ do
    commonFields
    div_ [class_ "row g-3 mt-2"] $ do
      div_ [class_ "col-md-4"] $ do
        label_ [class_ "form-label", for_ "amountValue"] "損害額 (最小通貨単位)"
        input_
          [ type_ "number"
          , name_ "amountValue"
          , id_ "amountValue"
          , class_ "form-control"
          , min_ "0"
          , required_ ""
          ]
      div_ [class_ "col-md-2"] $ do
        label_ [class_ "form-label", for_ "amountCurrency"] "通貨"
        select_
          [ name_ "amountCurrency"
          , id_ "amountCurrency"
          , class_ "form-select"
          , required_ ""
          ]
          $ do
            option_ [value_ "JPY"] "JPY"
            option_ [value_ "USD"] "USD"
            option_ [value_ "EUR"] "EUR"
      severitySelect "HIGH"
    div_ [class_ "mt-3"] $ do
      label_ [class_ "form-label", for_ "description"] "詳細"
      textarea_
        [ name_ "description"
        , id_ "description"
        , class_ "form-control"
        , rows_ "3"
        , required_ ""
        , placeholder_ "500 文字以内、必要に応じて写真 URL も本文中に記載"
        ]
        ""
    div_ [class_ "mt-3"] reporterFields
    submitCancelButtons

lossFormPage :: Html ()
lossFormPage = formLayout "紛失例外を登録"
  $ form_
    [ action_ "/exceptions/loss"
    , method_ "post"
    , makeAttribute "data-testid" "exception-form-loss"
    ]
  $ do
    commonFields
    div_ [class_ "row g-3 mt-2"] $ do
      div_ [class_ "col-md-4"] $ do
        label_ [class_ "form-label", for_ "amountValue"] "損失額 (最小通貨単位)"
        input_
          [ type_ "number"
          , name_ "amountValue"
          , id_ "amountValue"
          , class_ "form-control"
          , min_ "0"
          , required_ ""
          ]
      div_ [class_ "col-md-2"] $ do
        label_ [class_ "form-label", for_ "amountCurrency"] "通貨"
        select_
          [ name_ "amountCurrency"
          , id_ "amountCurrency"
          , class_ "form-select"
          , required_ ""
          ]
          $ do
            option_ [value_ "JPY"] "JPY"
            option_ [value_ "USD"] "USD"
      severitySelect "HIGH"
    div_ [class_ "mt-3"] $ do
      label_ [class_ "form-label", for_ "lastSeenAt"] "最終目視地点 (UN/LOCODE)"
      input_
        [ type_ "text"
        , name_ "lastSeenAt"
        , id_ "lastSeenAt"
        , class_ "form-control"
        , placeholder_ "5 文字英数大文字 (不明時は空欄)"
        , maxlength_ "5"
        ]
    div_ [class_ "mt-3"] reporterFields
    submitCancelButtons

-- | GET /exceptions/:id で対象が見つからないときに返すページ (US19/US20)
exceptionNotFoundPage :: Text -> Html ()
exceptionNotFoundPage eid = formLayout "例外詳細" $ do
  div_
    [ class_ "alert alert-warning"
    , makeAttribute "data-testid" "exception-not-found"
    ]
    $ do
      p_ [class_ "mb-0"] $ do
        "指定された例外 ID ("
        strong_ (toHtml eid)
        ") は見つかりませんでした。"
      p_
        [class_ "mt-2 mb-0 small text-muted"]
        "JSONB detail_json パーサ実装後、Postgres からの実データ表示が有効になります。"
