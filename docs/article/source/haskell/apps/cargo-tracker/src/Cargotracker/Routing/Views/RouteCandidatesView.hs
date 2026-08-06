{-# LANGUAGE OverloadedStrings #-}

{- | 経路候補一覧ビュー (US08a タスク 4.4, IT3)

`/bookings/:bookingId/routes` から表示される。`FoundRoute` リストを
rank 順に表形式で描画する。0 件のときは「期限内到達不可」を alert-warning
で表示する。
-}
module Cargotracker.Routing.Views.RouteCandidatesView
  ( routeCandidatesPage,
    routeCandidatesNotFoundPage,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (defaultTimeLocale, formatTime)
import Lucid

import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (unVoyageNumber)
import Cargotracker.Routing.Domain.Service.RouteFinder (FoundRoute (..))
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

routeCandidatesPage :: Text -> [FoundRoute] -> Html ()
routeCandidatesPage bid candidates =
  pageLayout "経路候補 - Cargo Tracker" $
    div_ [class_ "row justify-content-center"] $
      div_ [class_ "col-md-10"] $ do
        h1_ [class_ "h3 mb-4"] (toHtml ("経路候補: " <> bid))
        if null candidates
          then flashAlert FlashWarning "期限内に到達可能な経路が見つかりませんでした"
          else table_ [class_ "table table-sm table-striped"] $ do
            thead_ $
              tr_ $ do
                th_ "rank"
                th_ "区間数"
                th_ "出発時刻"
                th_ "到着時刻"
                th_ "航海番号"
            tbody_ $ mapM_ row candidates
        div_ [class_ "mt-4"] $ do
          a_
            [href_ ("/bookings/" <> bid), class_ "btn btn-secondary me-2"]
            "予約詳細に戻る"
          a_ [href_ "/", class_ "btn btn-light"] "トップへ"

row :: FoundRoute -> Html ()
row fr =
  tr_ $ do
    td_ (toHtml (T.pack (show (frRank fr))))
    td_ (toHtml (T.pack (show (frNumSegments fr))))
    td_ (toHtml (formatLocal (frFirstDeparture fr)))
    td_ (toHtml (formatLocal (frLastArrival fr)))
    td_
      ( toHtml
          ( T.intercalate
              ", "
              (map unVoyageNumber (frVoyageNumbers fr))
          )
      )
  where
    formatLocal = T.pack . formatTime defaultTimeLocale "%Y-%m-%d %H:%M"

routeCandidatesNotFoundPage :: Html ()
routeCandidatesNotFoundPage =
  pageLayout "Not Found - Cargo Tracker" $
    div_ [class_ "row justify-content-center"] $
      div_ [class_ "col-md-8"] $ do
        h1_ [class_ "h3 mb-4"] "予約が見つかりません"
        flashAlert FlashDanger "指定された予約 ID は存在しません"
        a_ [href_ "/bookings", class_ "btn btn-primary"] "予約一覧へ"
