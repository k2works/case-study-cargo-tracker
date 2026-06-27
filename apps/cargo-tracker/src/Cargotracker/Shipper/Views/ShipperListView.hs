{-# LANGUAGE OverloadedStrings #-}

-- | 荷主一覧ビュー (IT2 ナビゲーション)
module Cargotracker.Shipper.Views.ShipperListView
  ( shipperListPage,
  ) where

import qualified Data.Text as T
import Lucid

import Cargotracker.Shared.Web.Layout (pageLayout)
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank,
    CorporateNumber (..),
    Shipper (..),
    ShipperKind (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (ShipperName (..))

shipperListPage :: [Shipper] -> Html ()
shipperListPage shippers = pageLayout "荷主一覧 - Cargo Tracker" $ do
  div_ [class_ "d-flex justify-content-between align-items-center mb-4"] $ do
    h1_ [class_ "h3 mb-0"] "荷主一覧"
    a_ [href_ "/shippers/new", class_ "btn btn-primary"] "新規登録"
  if null shippers
    then p_ [class_ "text-muted"] "登録済みの荷主がありません。"
    else table_ [class_ "table table-striped"] $ do
      thead_ $ tr_ $ do
        th_ "荷主 ID"
        th_ "氏名 / 社名"
        th_ "メール"
        th_ "種別"
        th_ "法人番号 / ランク"
        th_ ""
      tbody_ (mapM_ row shippers)
  where
    row :: Shipper -> Html ()
    row s = do
      let ShipperId sid = shipperId s
          ShipperName nm = shipperName s
          ContactEmail em = shipperEmail s
      tr_ $ do
        td_ (toHtml sid)
        td_ (toHtml nm)
        td_ (toHtml em)
        td_ $ case shipperKind s of
          Individual -> "個人"
          Corporate _ _ -> "法人"
        td_ $ case shipperKind s of
          Individual -> mempty
          Corporate (CorporateNumber cn) rank ->
            toHtml (cn <> " / " <> rankText rank)
        td_ $
          a_ [href_ ("/shippers/" <> sid), class_ "btn btn-sm btn-outline-primary"] "詳細"

    rankText :: ContractRank -> T.Text
    rankText = T.pack . show
