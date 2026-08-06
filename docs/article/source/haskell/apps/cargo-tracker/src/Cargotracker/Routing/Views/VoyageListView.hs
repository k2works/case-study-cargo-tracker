{-# LANGUAGE OverloadedStrings #-}

-- | 航海一覧ビュー (IT2 ナビゲーション)
module Cargotracker.Routing.Views.VoyageListView
  ( voyageListPage,
  ) where

import qualified Data.Text as T
import Lucid

import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber (..))
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Shared.Web.Layout (pageLayout)

voyageListPage :: [Voyage] -> Html ()
voyageListPage voyages = pageLayout "航海一覧 - Cargo Tracker" $ do
  div_ [class_ "d-flex justify-content-between align-items-center mb-4"] $ do
    h1_ [class_ "h3 mb-0"] "航海一覧"
    a_ [href_ "/voyages/new", class_ "btn btn-primary"] "新規登録"
  if null voyages
    then p_ [class_ "text-muted"] "登録済みの航海がありません。"
    else table_ [class_ "table table-striped"] $ do
      thead_ $ tr_ $ do
        th_ "航海番号"
        th_ "区間数"
        th_ "バージョン"
        th_ ""
      tbody_ (mapM_ row voyages)
  where
    row :: Voyage -> Html ()
    row v = do
      let VoyageNumber vn = voyageNumber v
      tr_ $ do
        td_ (toHtml vn)
        td_ (toHtml (T.pack (show (length (carrierMovements v)))))
        td_ (toHtml (T.pack (show (voyageVersion v))))
        td_ $
          a_ [href_ ("/voyages/" <> vn), class_ "btn btn-sm btn-outline-primary"] "詳細"
