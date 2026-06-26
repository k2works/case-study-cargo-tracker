{-# LANGUAGE OverloadedStrings #-}

-- | 航海詳細ビュー (IT1)
module Cargotracker.Routing.Views.VoyageShowView
  ( voyageShowPage,
    voyageNotFoundPage,
  ) where

import qualified Data.Text as T
import Lucid

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber (..))
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

voyageShowPage :: Voyage -> Html ()
voyageShowPage v = pageLayout "航海詳細 - Cargo Tracker" $ do
  let VoyageNumber vn = voyageNumber v
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      h1_ [class_ "h3 mb-4"] (toHtml ("航海詳細: " <> vn))
      flashAlert FlashSuccess "登録しました"
      h2_ [class_ "h5 mt-4"] "区間一覧"
      table_ [class_ "table table-sm"] $ do
        thead_ $ tr_ $ do
          th_ "#"
          th_ "出発港"
          th_ "到着港"
          th_ "出発時刻"
          th_ "到着時刻"
        tbody_ $
          mapM_ (uncurry renderRow) (zip [1 :: Int ..] (carrierMovements v))
      a_ [href_ "/voyages/new", class_ "btn btn-secondary me-2"] "もう 1 件登録"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"
  where
    renderRow :: Int -> CarrierMovement -> Html ()
    renderRow i m = do
      let UnLocode dep = departureLocation m
          UnLocode arr = arrivalLocation m
      tr_ $ do
        td_ (toHtml (T.pack (show i)))
        td_ (toHtml dep)
        td_ (toHtml arr)
        td_ (toHtml (T.pack (show (departureTime m))))
        td_ (toHtml (T.pack (show (arrivalTime m))))

voyageNotFoundPage :: Html ()
voyageNotFoundPage = pageLayout "Not Found - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "航海が見つかりません"
      flashAlert FlashDanger "指定された航海番号は存在しません"
      a_ [href_ "/", class_ "btn btn-primary"] "トップへ"
