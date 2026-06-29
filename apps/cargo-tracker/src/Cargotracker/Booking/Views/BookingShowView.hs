{-# LANGUAGE OverloadedStrings #-}

-- | 貨物予約詳細ビュー (IT1)
module Cargotracker.Booking.Views.BookingShowView
  ( bookingShowPage,
    bookingNotFoundPage,
  ) where

import qualified Data.Text as T
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Booking.Domain.Model.Cargo (Cargo (..))
import Cargotracker.Booking.Domain.Model.CustomsDeclaration (CustomsDeclaration)
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Views.CustomsSectionView (customsSection)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

bookingShowPage :: Cargo -> Maybe CustomsDeclaration -> Html ()
bookingShowPage c mCustoms = pageLayout "貨物予約詳細 - Cargo Tracker" $ do
  let BookingId bid = cargoBookingId c
      ShipperRef sid = cargoShipperRef c
      route = cargoRouteSpec c
      UnLocode orig = origin route
      UnLocode dest = destination route
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] (toHtml ("予約詳細: " <> bid))
      flashAlert FlashSuccess "予約しました"
      table_ [class_ "table"] $ tbody_ $ do
        tr_ $ do
          th_ "予約 ID"
          td_ (toHtml bid)
        tr_ $ do
          th_ "荷主 ID"
          td_ (toHtml sid)
        tr_ $ do
          th_ "出発港"
          td_ (toHtml orig)
        tr_ $ do
          th_ "到着港"
          td_ (toHtml dest)
        tr_ $ do
          th_ "到着期限"
          td_ (toHtml (T.pack (show (arrivalDeadline route))))
        tr_ $ do
          th_ "状態"
          td_ (toHtml (T.pack (show (cargoStatus c))))
      -- US06 (IT3, H-03): Draft 状態は「予約を確定送信する」ボタン (POST /submit)。
      -- Submitted 状態は「経路設計者に引き渡す」ボタン (POST /handover)。
      -- いずれも PRG (303 → 同詳細画面?flash=...) に従う。
      case cargoStatus c of
        Draft ->
          form_
            [ action_ ("/bookings/" <> bid <> "/submit")
            , method_ "post"
            , class_ "d-inline"
            ]
            $ button_
              [type_ "submit", class_ "btn btn-primary me-2"]
              "予約を確定送信する"
        Submitted ->
          form_
            [ action_ ("/bookings/" <> bid <> "/handover")
            , method_ "post"
            , class_ "d-inline"
            ]
            $ button_
              [type_ "submit", class_ "btn btn-primary me-2"]
              "経路設計者に引き渡す"
        _ -> mempty
      -- US08a (IT3): 経路候補算出ページへの導線
      -- H-02 (2026-06-29 レビュー反映): ボタン文言から開発内部識別子 (US08a) を除去。
      -- ストーリー由来は data-story 属性で参照可能とし、業務ユーザーには見せない。
      a_
        [ href_ ("/bookings/" <> bid <> "/routes")
        , class_ "btn btn-outline-primary me-2"
        , makeAttribute "data-story" "US08a"
        ]
        "経路候補を見る"
      -- US27 (IT3): 通関情報セクションを埋め込む
      customsSection (cargoBookingId c) mCustoms
      a_ [href_ "/bookings/new", class_ "btn btn-secondary me-2"] "もう 1 件予約"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"

bookingNotFoundPage :: Html ()
bookingNotFoundPage = pageLayout "Not Found - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "貨物予約が見つかりません"
      flashAlert FlashDanger "指定された予約 ID は存在しません"
      a_ [href_ "/", class_ "btn btn-primary"] "トップへ"
