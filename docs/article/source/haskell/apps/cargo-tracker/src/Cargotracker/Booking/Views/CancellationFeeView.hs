{-# LANGUAGE OverloadedStrings #-}

{- | キャンセル料プレビュー / 確認用 Lucid view (US13, IT4)

予約詳細 (/bookings/:bookingId) の Confirmed 状態セクションに埋め込む
キャンセル料プレビュー部分と、キャンセル確認モーダル用フラグメント。

htmx パターン:
* `hx-get="/bookings/:id/cancel/preview"` で modal 展開時に動的取得
* POST `/bookings/:id/cancel` で PRG + flash 「キャンセルしました (料金 X%)」
-}
module Cargotracker.Booking.Views.CancellationFeeView
  ( feePreview,
    feePreviewFragment,
    cancelConfirmButton,
  ) where

import Data.Ratio (Rational, denominator, numerator)
import Data.Text (Text)
import qualified Data.Text as T
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
  )

-- | ティアを業務的なラベルに変換
tierLabel :: CancellationTier -> Text
tierLabel Free = "無料"
tierLabel Partial = "30% (一部料金)"
tierLabel Full = "100% (全額)"

-- | ティアに応じた Bootstrap アラート色
tierAlertClass :: CancellationTier -> Text
tierAlertClass Free = "alert-success"
tierAlertClass Partial = "alert-warning"
tierAlertClass Full = "alert-danger"

-- | Rational (例 30 % 100) を「30%」形式の文字列に整形
formatRate :: Rational -> Text
formatRate r =
  let pct = (fromIntegral (numerator r) :: Double) / fromIntegral (denominator r) * 100.0
   in T.pack (show (round pct :: Int) <> "%")

{- | 予約詳細ページに埋め込むキャンセル料プレビューブロック (Confirmed 状態専用)

`hx-get` で詳細プレビューを展開し、`#cancellation-fee-modal` に
描画する htmx パターン。
-}
feePreview :: BookingId -> Html ()
feePreview (BookingId bid) = div_ [class_ "mt-4", id_ "cancellation-section"] $ do
  h2_ [class_ "h5 mb-3"] "キャンセル"
  p_ [class_ "text-muted"] "予約をキャンセルする場合、出航日時に応じてキャンセル料が発生します。"
  div_ [id_ "cancellation-fee-modal", class_ "mb-3"] mempty
  button_
    [ type_ "button"
    , class_ "btn btn-outline-danger btn-sm"
    , makeAttribute "hx-get" ("/bookings/" <> bid <> "/cancel/preview")
    , makeAttribute "hx-target" "#cancellation-fee-modal"
    , makeAttribute "hx-swap" "innerHTML"
    ]
    "キャンセル料を確認"

{- | htmx で読み込む fragment: 現在のキャンセル料 + 確認ボタン

`feePreview` の `hx-target="#cancellation-fee-modal"` に挿入される
HTML 断片。ハンドラ側で CancelBookingCommand (実行せず) の見積を
計算して本 view に渡す。
-}
feePreviewFragment :: BookingId -> CancellationFee -> Html ()
feePreviewFragment bid fee = do
  div_ [class_ ("alert " <> tierAlertClass (cfTier fee))] $ do
    strong_ "現時点のキャンセル料: "
    span_ (toHtml (tierLabel (cfTier fee) <> " (" <> formatRate (cfRate fee) <> ")"))
  cancelConfirmButton bid

{- | キャンセル確定ボタン (POST /bookings/:id/cancel)

PRG パターンで成功時は予約詳細にリダイレクト + flash 表示。
-}
cancelConfirmButton :: BookingId -> Html ()
cancelConfirmButton (BookingId bid) =
  form_
    [ method_ "post"
    , action_ ("/bookings/" <> bid <> "/cancel")
    , class_ "d-inline"
    ]
    $ button_
      [ type_ "submit"
      , class_ "btn btn-danger btn-sm"
      , makeAttribute "data-confirm" "本当にキャンセルしますか?"
      ]
      "キャンセルを確定"
