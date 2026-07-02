{-# LANGUAGE OverloadedStrings #-}

{- | 料金算出画面 (US21, IT6)

`/pricing/calculate` に配置される Lucid HTML ビュー。
入力フォーム (貨物カテゴリ + 距離 + 重量 + 割引率 + 対象通貨) と
計算結果 (基準通貨 → 対象通貨の Cost) を表示する。
-}
module Cargotracker.Pricing.Views.CostCalculationView
  ( costCalculationPage,
    CalculationResultView (..),
  ) where

import qualified Data.Foldable as F
import Data.Text (Text)
import qualified Data.Text as T
import Lucid
import Lucid.Base (makeAttribute)

-- | 表示用の計算結果 VO (成功なら Cost + 通貨、失敗ならエラー内容)。
data CalculationResultView
  = ResultSuccess !Integer !Text -- amount + currency
  | ResultError !Text
  deriving stock (Eq, Show)

costCalculationPage :: Maybe CalculationResultView -> Html ()
costCalculationPage mResult = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ "輸送料金算出 - Cargo Tracker"
    link_
      [ rel_ "stylesheet"
      , href_ "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
      ]
  body_ $ div_ [class_ "container my-5"] $ do
    h1_ [class_ "mb-4"] "輸送料金算出"

    formCard

    F.for_ mResult resultCard
  where
    formCard =
      div_ [class_ "card shadow-sm mb-4"] $ div_ [class_ "card-body"] $ do
        h5_ [class_ "card-title"] "算出条件"
        form_ [method_ "post", action_ "/pricing/calculate"] $ do
          div_ [class_ "row g-3"] $ do
            div_ [class_ "col-md-4"] $ do
              label_ [class_ "form-label", for_ "cargoCategory"] "貨物カテゴリ"
              select_ [name_ "cargoCategory", id_ "cargoCategory", class_ "form-select"] $ do
                option_ [value_ "General"] "General (一般貨物)"
                option_ [value_ "Refrigerated"] "Refrigerated (冷凍/冷蔵)"
                option_ [value_ "Hazardous"] "Hazardous (危険物)"

            div_ [class_ "col-md-4"] $ do
              label_ [class_ "form-label", for_ "distanceKm"] "距離 (km)"
              input_
                [ type_ "number"
                , name_ "distanceKm"
                , id_ "distanceKm"
                , class_ "form-control"
                , min_ "0"
                , value_ "0"
                , required_ ""
                ]

            div_ [class_ "col-md-4"] $ do
              label_ [class_ "form-label", for_ "weightKg"] "重量 (kg)"
              input_
                [ type_ "number"
                , name_ "weightKg"
                , id_ "weightKg"
                , class_ "form-control"
                , min_ "0"
                , value_ "0"
                , required_ ""
                ]

            div_ [class_ "col-md-4"] $ do
              label_ [class_ "form-label", for_ "baseCurrency"] "基準通貨"
              input_
                [ type_ "text"
                , name_ "baseCurrency"
                , id_ "baseCurrency"
                , class_ "form-control"
                , value_ "JPY"
                , pattern_ "[A-Z]{3}"
                , required_ ""
                ]

            div_ [class_ "col-md-4"] $ do
              label_ [class_ "form-label", for_ "targetCurrency"] "対象通貨"
              input_
                [ type_ "text"
                , name_ "targetCurrency"
                , id_ "targetCurrency"
                , class_ "form-control"
                , value_ "JPY"
                , pattern_ "[A-Z]{3}"
                , required_ ""
                ]

            div_ [class_ "col-md-4"] $ do
              label_ [class_ "form-label", for_ "discountRate"] "割引率 (%)"
              input_
                [ type_ "number"
                , name_ "discountRate"
                , id_ "discountRate"
                , class_ "form-control"
                , min_ "0"
                , max_ "100"
                , value_ "0"
                , required_ ""
                ]

          div_ [class_ "mt-4"] $
            button_
              [type_ "submit", class_ "btn btn-primary"]
              "料金を算出"

    resultCard :: CalculationResultView -> Html ()
    resultCard (ResultSuccess amount currency) =
      div_
        [ class_ "card border-success shadow-sm"
        , makeAttribute "data-testid" "calc-result-success"
        ]
        (div_ [class_ "card-body"] (successBody amount currency))
    resultCard (ResultError msg) =
      div_
        [ class_ "alert alert-danger"
        , makeAttribute "data-testid" "calc-result-error"
        ]
        (errorBody msg)

    successBody :: Integer -> Text -> Html ()
    successBody amount currency = do
      h5_ [class_ "card-title text-success"] "算出結果"
      p_ [class_ "display-6 mb-0"] $ do
        span_
          [makeAttribute "data-testid" "calc-amount"]
          (toHtml (T.pack (show amount)))
        span_ [class_ "ms-2 fs-5 text-muted"] (toHtml currency)

    errorBody :: Text -> Html ()
    errorBody msg = do
      strong_ "エラー: "
      toHtml msg
