{-# LANGUAGE OverloadedStrings #-}

{- | 予約詳細の通関情報セクション (US27, IT3)

予約詳細画面 (/bookings/:bookingId) に組み込まれる通関情報パーシャル。

* 通関情報が未登録: 「未登録」表示 + 「通関情報を登録」リンク (-> /bookings/:bid/customs/edit)
* 登録済み: HS コード / 通関業者 / 申告ステータスを読み取り表示 + 「編集」リンク
* 編集ページは customsEditPage で表示し、POST /bookings/:bid/customs で確定。
-}
module Cargotracker.Booking.Views.CustomsSectionView
  ( customsSection,
    customsEditPage,
  ) where

import Data.Text (Text)
import Lucid

import Cargotracker.Booking.Domain.Model.CustomsDeclaration
  ( CustomsDeclaration (..),
  )
import Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( DeclarationStatus (..),
    declarationStatusToText,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.HsCode (unHsCode)
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

-- | 予約詳細に埋め込む通関情報セクション (読み取り)
customsSection :: BookingId -> Maybe CustomsDeclaration -> Html ()
customsSection (BookingId bid) mCd = div_ [class_ "mt-4", id_ "customs-section"] $ do
  h2_ [class_ "h5 mb-3"] "通関情報"
  case mCd of
    Nothing -> do
      p_ [class_ "text-muted"] "通関情報は未登録です。"
      a_
        [ href_ ("/bookings/" <> bid <> "/customs/edit")
        , class_ "btn btn-outline-primary btn-sm"
        ]
        "通関情報を登録"
    Just cd -> do
      table_ [class_ "table table-sm"] $ tbody_ $ do
        tr_ $ do
          th_ "HS コード"
          td_ (toHtml (unHsCode (cdHsCode cd)))
        tr_ $ do
          th_ "通関業者"
          td_ (toHtml (cdBrokerName cd))
        tr_ $ do
          th_ "申告ステータス"
          td_ (statusBadge (cdStatus cd))
      a_
        [ href_ ("/bookings/" <> bid <> "/customs/edit")
        , class_ "btn btn-outline-secondary btn-sm"
        ]
        "通関情報を編集"

statusBadge :: DeclarationStatus -> Html ()
statusBadge s =
  span_ [class_ ("badge " <> cls)] (toHtml (declarationStatusToText s))
  where
    cls = case s of
      Pending -> "bg-secondary"
      Cleared -> "bg-success"
      Held -> "bg-warning text-dark"
      Rejected -> "bg-danger"

-- | 通関情報編集フォームページ (新規/更新兼用)
customsEditPage ::
  BookingId ->
  Maybe CustomsDeclaration ->
  Maybe Text ->
  Html ()
customsEditPage (BookingId bid) mCd mError =
  pageLayout "通関情報編集 - Cargo Tracker" $
    div_ [class_ "row justify-content-center"] $
      div_ [class_ "col-md-8"] $ do
        h1_ [class_ "h3 mb-4"] (toHtml ("通関情報編集: " <> bid))
        case mError of
          Just msg -> flashAlert FlashDanger msg
          Nothing -> mempty
        form_
          [action_ ("/bookings/" <> bid <> "/customs"), method_ "post"]
          $ do
            div_ [class_ "mb-3"] $ do
              label_ [for_ "hs_code", class_ "form-label"] "HS コード (6-10 桁の数字)"
              input_
                [ type_ "text"
                , id_ "hs_code"
                , name_ "hs_code"
                , class_ "form-control"
                , required_ "required"
                , maxlength_ "10"
                , value_ (maybe "" (unHsCode . cdHsCode) mCd)
                , placeholder_ "123456"
                ]
            div_ [class_ "mb-3"] $ do
              label_ [for_ "broker_name", class_ "form-label"] "通関業者名 (最大 100 文字)"
              input_
                [ type_ "text"
                , id_ "broker_name"
                , name_ "broker_name"
                , class_ "form-control"
                , required_ "required"
                , maxlength_ "100"
                , value_ (maybe "" cdBrokerName mCd)
                ]
            div_ [class_ "mb-3"] $ do
              label_ [for_ "status", class_ "form-label"] "申告ステータス"
              select_ [id_ "status", name_ "status", class_ "form-select"] $
                mapM_ (statusOption (fmap cdStatus mCd)) [Pending, Cleared, Held, Rejected]
            div_ [class_ "mt-4"] $ do
              button_ [type_ "submit", class_ "btn btn-primary me-2"] "保存"
              a_
                [href_ ("/bookings/" <> bid), class_ "btn btn-light"]
                "キャンセル"

statusOption :: Maybe DeclarationStatus -> DeclarationStatus -> Html ()
statusOption mCurrent s =
  let txt = declarationStatusToText s
      attrs = [value_ txt] <> [selected_ "selected" | mCurrent == Just s]
   in option_ attrs (toHtml txt)
