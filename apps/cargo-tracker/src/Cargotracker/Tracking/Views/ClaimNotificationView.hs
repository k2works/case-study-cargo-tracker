{-# LANGUAGE OverloadedStrings #-}

{- | 引取通知 印刷用ビュー (T5-05, US26 暫定策, IT6)

荷受人向けに発行された確認コードを印刷用にレンダリングする HTML ビュー。
Notification BC 本格実装 (US26 本体) までの暫定配信手段。

用途:
- 引取窓口で管理者が予約情報を検索してこのページを開き、印刷して荷受人に手渡す
- あるいは PDF に出力して荷受人にメール送信 (メール本体は Notification BC で本実装)

ペイロード:
- BookingId (予約番号)
- ConfirmationCode (6 桁数字。DB には bcrypt hash のみ保存されるため、
  発行時に一度だけ Application 層から受け取って表示する)
- 引取場所 (UN/LOCODE)

注意 (SEC-04):
- 本ビューは平文の確認コードを含む。表示以外の場所 (ログ / URL / 履歴) に
  漏れないよう、呼出側 (ハンドラ) は POST + hidden form か Cookie 経由で
  値を受け渡すことを推奨する
-}
module Cargotracker.Tracking.Views.ClaimNotificationView
  ( claimNotificationPage,
    ClaimNotificationPayload (..),
  ) where

import Data.Text (Text)
import Lucid
import Lucid.Base (makeAttribute)

data ClaimNotificationPayload = ClaimNotificationPayload
  { cnpBookingId :: !Text
  , cnpConfirmationCode :: !Text
  -- ^ 6 桁数字の平文。表示のためだけに保持し、ログ・URL への埋込は避ける。
  , cnpLocationUnlocode :: !Text
  , cnpConsigneeName :: !(Maybe Text)
  }
  deriving stock (Eq, Show)

claimNotificationPage :: ClaimNotificationPayload -> Html ()
claimNotificationPage p = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ "引取通知 - Cargo Tracker"
    link_
      [ rel_ "stylesheet"
      , href_ "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
      ]
    style_ printStyles
  body_ $ do
    div_ [class_ "container my-5", makeAttribute "data-testid" "claim-notification"] $ do
      div_ [class_ "d-flex justify-content-between mb-4 no-print"] $ do
        h4_ [class_ "m-0"] "Cargo Tracker - 引取通知"
        button_
          [ class_ "btn btn-outline-primary btn-sm"
          , makeAttribute "onclick" "window.print()"
          ]
          "印刷"

      div_ [class_ "card shadow-sm"] $ div_ [class_ "card-body p-4"] $ do
        h2_ [class_ "card-title mb-4"] "貨物引取のご案内"

        p_
          [class_ "lead"]
          "下記の予約番号と確認コードをお持ちください。引取窓口で本紙をご提示いただくと、貨物の引取り手続きが可能です。"

        dl_ [class_ "row mt-4"] $ do
          dt_ [class_ "col-sm-4"] "予約番号"
          dd_ [class_ "col-sm-8"] $
            code_ [class_ "fs-5"] (toHtml (cnpBookingId p))

          case cnpConsigneeName p of
            Just name -> do
              dt_ [class_ "col-sm-4"] "荷受人"
              dd_ [class_ "col-sm-8"] (toHtml name)
            Nothing -> pure ()

          dt_ [class_ "col-sm-4"] "引取場所 (UN/LOCODE)"
          dd_ [class_ "col-sm-8"] $
            code_ [class_ "fs-5"] (toHtml (cnpLocationUnlocode p))

          dt_ [class_ "col-sm-4"] "確認コード"
          dd_ [class_ "col-sm-8"] $
            span_
              [ class_ "fs-3 fw-bold text-primary"
              , makeAttribute "data-testid" "confirmation-code"
              ]
              (toHtml (cnpConfirmationCode p))

        hr_ [class_ "my-4"]

        div_ [class_ "alert alert-warning small mb-0"] $ do
          strong_ "ご注意: "
          "確認コードは第三者に開示しないでください。5 回の入力ミスでロックされます。"

      p_
        [class_ "text-muted small mt-4 text-center no-print"]
        "本通知は暫定配信のプレビューです (T5-05 IT6)。US26 Notification BC 実装時にメール送信に置き換わります。"
  where
    printStyles =
      "@media print { .no-print { display: none !important; } body { background: white; } }"
