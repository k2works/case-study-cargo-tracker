{-# LANGUAGE OverloadedStrings #-}

-- | 貨物予約一覧ビュー (IT2 ナビゲーション)
module Cargotracker.Booking.Views.BookingListView
  ( bookingListPage,
  ) where

import qualified Data.Text as T
import Lucid

import Cargotracker.Booking.Domain.Model.Cargo (Cargo (..))
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.CargoType
  ( cargoTypeToText,
  )
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))
import Cargotracker.Shared.Web.Layout (pageLayout)

bookingListPage :: [Cargo] -> Html ()
bookingListPage cargos = pageLayout "貨物予約一覧 - Cargo Tracker" $ do
  div_ [class_ "d-flex justify-content-between align-items-center mb-4"] $ do
    h1_ [class_ "h3 mb-0"] "貨物予約一覧"
    a_ [href_ "/bookings/new", class_ "btn btn-primary"] "新規予約"
  if null cargos
    then p_ [class_ "text-muted"] "予約がありません。"
    else table_ [class_ "table table-striped"] $ do
      thead_ $ tr_ $ do
        th_ "予約 ID"
        th_ "荷主 ID"
        th_ "出発港"
        th_ "到着港"
        th_ "状態"
        th_ "貨物種別"
        th_ ""
      tbody_ (mapM_ row cargos)
  where
    row :: Cargo -> Html ()
    row c = do
      let BookingId bid = cargoBookingId c
          ShipperRef sid = cargoShipperRef c
          route = cargoRouteSpec c
          UnLocode o = origin route
          UnLocode d = destination route
      tr_ $ do
        td_ (toHtml bid)
        td_ (toHtml sid)
        td_ (toHtml o)
        td_ (toHtml d)
        td_ (toHtml (T.pack (show (cargoStatus c))))
        td_ (toHtml (cargoTypeToText (cargoType c)))
        td_ $
          a_ [href_ ("/bookings/" <> bid), class_ "btn btn-sm btn-outline-primary"] "詳細"
