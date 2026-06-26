{-# LANGUAGE OverloadedStrings #-}

-- | 貨物予約詳細ビュー (IT1)
module Cargotracker.Booking.Views.BookingShowView
  ( bookingShowPage,
    bookingNotFoundPage,
  ) where

import qualified Data.Text as T
import Lucid

import Cargotracker.Booking.Domain.Model.Cargo (Cargo (..))
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))

bookingShowPage :: Cargo -> Html ()
bookingShowPage c = pageLayout "貨物予約詳細 - Cargo Tracker" $ do
  let BookingId bid = cargoBookingId c
      ShipperId sid = cargoShipperId c
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
      a_ [href_ "/bookings/new", class_ "btn btn-secondary me-2"] "もう 1 件予約"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"

bookingNotFoundPage :: Html ()
bookingNotFoundPage = pageLayout "Not Found - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "貨物予約が見つかりません"
      flashAlert FlashDanger "指定された予約 ID は存在しません"
      a_ [href_ "/", class_ "btn btn-primary"] "トップへ"
