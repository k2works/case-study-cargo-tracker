{-# LANGUAGE OverloadedStrings #-}

-- | 公開追跡ページ ビュー (US18, IT5)
module Cargotracker.Tracking.Views.PublicTrackingView
  ( publicTrackingSearchPage,
    publicTrackingDetailPage,
    publicTrackingNotFoundPage,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Shared.Domain.TransportStatus
  ( TransportStatus (..),
  )
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)
import Cargotracker.Tracking.Application.QueryTrackingByNumberQuery
  ( TrackingView (..),
  )

-- | 追跡番号入力フォーム (トップページ相当、公開・認証不要)。
publicTrackingSearchPage :: Html ()
publicTrackingSearchPage = pageLayout "貨物追跡 - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-6"] $ do
      h1_ [class_ "h3 mb-4"] "貨物追跡"
      p_ [class_ "text-muted"] "予約確定時に発行された追跡番号 (例: TR + 6 文字英数) を入力してください。"
      form_
        [ action_ "/public/tracking"
        , method_ "get"
        , class_ "mb-3"
        , makeAttribute "data-story" "US18"
        ]
        $ do
          div_ [class_ "input-group"] $ do
            input_
              [ type_ "text"
              , name_ "trackingNumber"
              , class_ "form-control"
              , placeholder_ "TRA1B2C3"
              , makeAttribute "pattern" "[A-Z0-9]{8}"
              , required_ ""
              ]
            button_ [type_ "submit", class_ "btn btn-primary"] "追跡する"

-- | 追跡詳細ページ (公開・認証不要、追跡番号が存在するとき)。
publicTrackingDetailPage :: TrackingView -> Html ()
publicTrackingDetailPage view = pageLayout ("追跡: " <> tvTrackingNumber view) $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] $ do
        toHtml ("追跡番号: " :: Text)
        code_ [class_ "fs-4"] (toHtml (tvTrackingNumber view))
      flashAlert FlashSuccess (statusLabelJa (tvStatus view))
      table_ [class_ "table", makeAttribute "data-story" "US18"] $ tbody_ $ do
        tr_ $ do
          th_ "追跡番号"
          td_ (code_ (toHtml (tvTrackingNumber view)))
        tr_ $ do
          th_ "予約番号"
          td_ (toHtml (tvBookingId view))
        tr_ $ do
          th_ "輸送状態"
          td_
            [ classes_
                [ "fw-bold"
                , statusColorClass (tvStatus view)
                ]
            ]
            (toHtml (statusLabelJa (tvStatus view)))
        tr_ $ do
          th_ "内部ステータス"
          td_ (code_ (toHtml (tvStatusText view)))
      a_ [href_ "/public/tracking", class_ "btn btn-secondary"] "別の番号を追跡"

-- | 追跡番号が見つからない場合の 404 相当ページ。
publicTrackingNotFoundPage :: Text -> Html ()
publicTrackingNotFoundPage input = pageLayout "追跡番号が見つかりません - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-6"] $ do
      h1_ [class_ "h3 mb-4"] "追跡番号が見つかりません"
      flashAlert
        FlashWarning
        ( "入力された追跡番号 「"
            <> input
            <> "」 は登録されていません。番号を確認してもう一度お試しください。"
        )
      p_
        [class_ "text-muted small"]
        "追跡番号は 8 文字の英数大文字 (例: TRA1B2C3) です。"
      a_ [href_ "/public/tracking", class_ "btn btn-primary"] "再入力"

-- | TransportStatus を日本語ラベルに変換 (公開ページ向け)。
statusLabelJa :: TransportStatus -> Text
statusLabelJa TsNotReceived = "受領待ち"
statusLabelJa TsReceived = "受領済"
statusLabelJa TsLoaded = "積込済"
statusLabelJa TsOnboardCarrier = "輸送中"
statusLabelJa TsUnloaded = "荷降し済"
statusLabelJa TsAwaitingClaim = "引取待ち"
statusLabelJa TsClaimed = "引取済"
statusLabelJa TsInException = "例外発生・対応中"
statusLabelJa TsUnknown = "不明"

-- | 状態に応じた Bootstrap カラー class。
statusColorClass :: TransportStatus -> Text
statusColorClass TsClaimed = "text-success"
statusColorClass TsInException = "text-danger"
statusColorClass TsUnknown = "text-secondary"
statusColorClass _ = "text-primary"
