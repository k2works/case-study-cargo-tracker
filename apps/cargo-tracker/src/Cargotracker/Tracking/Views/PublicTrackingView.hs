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

import Cargotracker.Handling.Application.QueryHandlingHistoryQuery
  ( HandlingEventView (..),
  )
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

-- | 追跡詳細ページ (公開・認証不要、追跡番号が存在するとき + 荷役履歴タイムライン)。
publicTrackingDetailPage :: TrackingView -> [HandlingEventView] -> Html ()
publicTrackingDetailPage view events = pageLayout ("追跡: " <> tvTrackingNumber view) $
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
      timelineSection events
      a_ [href_ "/public/tracking", class_ "btn btn-secondary"] "別の番号を追跡"

{- | 荷役履歴タイムライン (US18 拡張、IT5)。
イベントがない場合は「まだ荷役イベントは記録されていません」を表示。
-}
timelineSection :: [HandlingEventView] -> Html ()
timelineSection [] =
  div_ [class_ "mt-4 mb-3", makeAttribute "data-story" "US18-timeline"] $ do
    h2_ [class_ "h5"] "荷役履歴"
    p_ [class_ "text-muted"] "まだ荷役イベントは記録されていません。"
timelineSection events =
  div_ [class_ "mt-4 mb-3", makeAttribute "data-story" "US18-timeline"] $ do
    h2_ [class_ "h5"] "荷役履歴"
    ol_ [class_ "list-group list-group-numbered"] $
      mapM_ eventRow events
  where
    eventRow :: HandlingEventView -> Html ()
    eventRow ev = li_ [class_ "list-group-item d-flex justify-content-between align-items-start"] $ do
      div_ [class_ "ms-2 me-auto"] $ do
        div_ [class_ "fw-bold"] $ do
          toHtml (handlingTypeLabelJa (hevEventType ev))
          span_ [class_ "text-muted ms-2 small"] (code_ (toHtml (hevEventType ev)))
        span_ [class_ "small"] $ do
          toHtml (T.pack (show (hevCompletionTime ev)))
          toHtml (" @ " :: Text)
          code_ (toHtml (hevLocationUnlocode ev))
          case hevVoyageNumber ev of
            Just vn -> do
              toHtml (" / 航海 " :: Text)
              code_ (toHtml vn)
            Nothing -> mempty
      span_ [class_ "badge bg-secondary rounded-pill"] (toHtml (hevOperatorName ev))

-- | HandlingType Text を日本語ラベル化。
handlingTypeLabelJa :: Text -> Text
handlingTypeLabelJa "RECEIVE" = "貨物受領"
handlingTypeLabelJa "LOAD" = "積込"
handlingTypeLabelJa "UNLOAD" = "荷降し"
handlingTypeLabelJa "CUSTOMS" = "通関手続完了"
handlingTypeLabelJa "CLAIM" = "引取完了"
handlingTypeLabelJa other = other

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
