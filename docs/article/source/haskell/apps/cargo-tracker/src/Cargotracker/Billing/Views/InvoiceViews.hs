{-# LANGUAGE OverloadedStrings #-}

{- | 請求書画面群 (US23, IT8)

ui_design.md 画面一覧 (/billing/invoices 系) と iteration_plan-8 の
salt ワイヤーフレーム 3 種に対応する。

- invoiceListPage: 請求書一覧 (payment_status フィルタ)
- invoiceNewPage: 新規請求書発行 (引取済予約の選択・料金内訳表示)
- invoiceDetailPage: 請求書詳細 (明細 + 入金発行 + 入金確認)

Billing Domain 型を直接受け取らず Text-DTO (InvoiceRow) に落とす (Rule 4)。
-}
module Cargotracker.Billing.Views.InvoiceViews
  ( InvoiceRow (..),
    invoiceListPage,
    invoiceNewPage,
    invoiceDetailPage,
  ) where

import qualified Data.Foldable as F
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import Lucid

-- | 表示用の請求書行 (Text-DTO)
data InvoiceRow = InvoiceRow
  { irInvoiceNumber :: !Text
  , irBookingId :: !Text
  , irShipperId :: !Text
  , irBaseAmount :: !Text
  -- ^ 表示整形済み (例: "485,000 JPY")
  , irDiscountRate :: !Text
  -- ^ 例: "10%"
  , irFinalAmount :: !Text
  , irStatus :: !Text
  -- ^ PENDING / CONFIRMED / OVERDUE / REFUNDED
  , irDueDate :: !Text
  -- ^ 未設定は "-"
  , irPaidAt :: !Text
  }
  deriving stock (Eq, Show)

pageShell :: Text -> Html () -> Html ()
pageShell titleText content = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ (toHtml (titleText <> " - Cargo Tracker"))
    link_ [rel_ "stylesheet", href_ "/static/css/bootstrap.min.css"]
  body_ $
    div_ [class_ "container my-4"] content

flashAlert :: Maybe Text -> Html ()
flashAlert Nothing = pure ()
flashAlert (Just msg) =
  div_ [class_ cls, role_ "alert"] (toHtml body)
  where
    (cls, body) = case msg of
      "issued" -> ("alert alert-success", "請求書を発行しました" :: Text)
      "payment-issued" -> ("alert alert-success", "入金発行しました (支払期日と reference_code を設定)")
      "confirmed" -> ("alert alert-success", "入金を確認しました。予約は精算済 (Settled) になりました")
      "not-delivered" -> ("alert alert-danger", "引取完了前の予約には請求書を発行できません")
      "already-exists" -> ("alert alert-danger", "この予約の請求書は既に発行済です")
      "reference-mismatch" -> ("alert alert-danger", "reference_code が一致しません")
      "already-confirmed" -> ("alert alert-danger", "この請求書は既に入金確認済です")
      "not-found" -> ("alert alert-danger", "該当する請求書が見つかりません")
      other -> ("alert alert-warning", other)

-- | 請求書一覧 /billing/invoices
invoiceListPage :: Maybe Text -> Maybe Text -> [InvoiceRow] -> Html ()
invoiceListPage mFlash mStatusFilter rows = pageShell "請求書一覧" $ do
  h1_ [class_ "mb-4"] "請求書一覧"
  flashAlert mFlash
  form_ [method_ "get", action_ "/billing/invoices", class_ "row g-2 mb-3"] $ do
    div_ [class_ "col-auto"] $
      select_ [name_ "status", class_ "form-select"] $ do
        statusOption "" "すべて"
        statusOption "PENDING" "Pending (未入金)"
        statusOption "CONFIRMED" "Confirmed (入金確認済)"
        statusOption "OVERDUE" "Overdue (期限超過)"
        statusOption "REFUNDED" "Refunded (返金済)"
    div_ [class_ "col-auto"] $
      button_ [type_ "submit", class_ "btn btn-outline-secondary"] "絞り込み"
  if null rows
    then p_ [class_ "text-muted"] "請求書はまだありません。引取済の予約から発行できます。"
    else table_ [class_ "table table-striped align-middle"] $ do
      thead_ $ tr_ $ do
        th_ "請求番号"
        th_ "予約"
        th_ "請求金額"
        th_ "状態"
        th_ "支払期限"
        th_ ""
      tbody_ $ F.for_ rows $ \r -> tr_ $ do
        td_ (toHtml (irInvoiceNumber r))
        td_ (toHtml (irBookingId r))
        td_ (toHtml (irFinalAmount r))
        td_ (statusBadge (irStatus r))
        td_ (toHtml (irDueDate r))
        td_ $
          a_
            [ href_ ("/billing/invoices/" <> irInvoiceNumber r)
            , class_ "btn btn-sm btn-outline-primary"
            ]
            "詳細"
  a_ [href_ "/billing/invoices/new", class_ "btn btn-primary mt-3"] "+ 請求書を発行する"
  where
    statusOption :: Text -> Text -> Html ()
    statusOption v label =
      option_
        ([value_ v] <> [selected_ "selected" | mStatusFilter == Just v && v /= ""])
        (toHtml label)

statusBadge :: Text -> Html ()
statusBadge status = span_ [class_ ("badge " <> cls)] (toHtml status)
  where
    cls = case status of
      "PENDING" -> "bg-info"
      "CONFIRMED" -> "bg-success"
      "OVERDUE" -> "bg-danger"
      "REFUNDED" -> "bg-secondary"
      _ -> "bg-light text-dark"

{- | 新規請求書発行 /billing/invoices/new

bookingId 指定時は Pricing BC で算出済みの料金内訳 (Text 整形済) を表示する。
-}
invoiceNewPage ::
  Maybe Text ->
  -- | 選択中 bookingId
  Maybe Text ->
  -- | 料金内訳 (label, amount) 。未選択時は空
  [(Text, Text)] ->
  Html ()
invoiceNewPage mFlash mBookingId amounts = pageShell "請求書を発行" $ do
  h1_ [class_ "mb-4"] "請求書を発行 (引取済予約から)"
  flashAlert mFlash
  form_ [method_ "get", action_ "/billing/invoices/new", class_ "row g-2 mb-3"] $ do
    div_ [class_ "col-auto"] $
      input_
        [ type_ "text"
        , name_ "bookingId"
        , class_ "form-control"
        , placeholder_ "BK-XXXXXX"
        , value_ (fromMaybe "" mBookingId)
        ]
    div_ [class_ "col-auto"] $
      button_ [type_ "submit", class_ "btn btn-outline-secondary"] "料金を確認"
  case mBookingId of
    Nothing -> p_ [class_ "text-muted"] "引取済の予約番号を入力してください。"
    Just bid -> do
      h2_ [class_ "h5"] (toHtml ("予約 " <> bid <> " の料金 (Pricing BC 自動取得)"))
      table_ [class_ "table w-auto"] $
        tbody_ $
          F.for_ amounts $ \(label, amount) -> tr_ $ do
            th_ (toHtml label)
            td_ [class_ "text-end"] (toHtml amount)
      form_ [method_ "post", action_ "/billing/invoices"] $ do
        input_ [type_ "hidden", name_ "bookingId", value_ bid]
        button_ [type_ "submit", class_ "btn btn-primary"] "発行する"
        a_ [href_ "/billing/invoices", class_ "btn btn-link"] "キャンセル"

-- | 請求書詳細 /billing/invoices/:invoiceId
invoiceDetailPage :: Maybe Text -> InvoiceRow -> Html ()
invoiceDetailPage mFlash r = pageShell "請求書詳細" $ do
  h1_ [class_ "mb-4"] (toHtml ("請求書詳細 " <> irInvoiceNumber r))
  flashAlert mFlash
  table_ [class_ "table w-auto"] $
    tbody_ $ do
      row "予約番号" (irBookingId r)
      row "荷主" (irShipperId r)
      row "基本料金" (irBaseAmount r)
      row "割引" (irDiscountRate r)
      row "請求金額" (irFinalAmount r)
      tr_ $ th_ "状態" >> td_ (statusBadge (irStatus r))
      row "支払期限" (irDueDate r)
      row "入金日時" (irPaidAt r)
  case irStatus r of
    "PENDING"
      | irDueDate r == "-" -> issuePaymentForm
      | otherwise -> confirmPaymentForm
    "OVERDUE" -> confirmPaymentForm
    _ -> pure ()
  a_ [href_ "/billing/invoices", class_ "btn btn-link mt-3"] "一覧へ戻る"
  where
    row :: Text -> Text -> Html ()
    row label v = tr_ $ th_ (toHtml label) >> td_ (toHtml v)
    issuePaymentForm = do
      h2_ [class_ "h5 mt-4"] "入金発行 (支払期日 + reference_code の設定)"
      form_
        [ method_ "post"
        , action_ ("/billing/invoices/" <> irInvoiceNumber r <> "/issue-payment")
        , class_ "row g-2"
        ]
        $ do
          div_ [class_ "col-auto"] $
            input_ [type_ "date", name_ "dueDate", class_ "form-control", required_ ""]
          div_ [class_ "col-auto"] $
            input_
              [ type_ "text"
              , name_ "paymentReference"
              , class_ "form-control"
              , placeholder_ "PAY-REF-XXXXXX"
              , required_ ""
              ]
          div_ [class_ "col-auto"] $
            button_ [type_ "submit", class_ "btn btn-primary"] "入金発行する"
    confirmPaymentForm = do
      h2_ [class_ "h5 mt-4"] "入金確認"
      p_ [class_ "text-muted"] "入金確認により予約状態が「精算済 (Settled)」に更新されます"
      form_
        [ method_ "post"
        , action_ ("/billing/invoices/" <> irInvoiceNumber r <> "/confirm-payment")
        , class_ "row g-2"
        ]
        $ do
          div_ [class_ "col-auto"] $
            input_
              [ type_ "text"
              , name_ "paymentReference"
              , class_ "form-control"
              , placeholder_ "reference_code"
              , required_ ""
              ]
          div_ [class_ "col-auto"] $
            button_ [type_ "submit", class_ "btn btn-success"] "入金確認する"
