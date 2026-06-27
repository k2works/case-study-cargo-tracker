{-# LANGUAGE OverloadedStrings #-}

-- | 荷主詳細ビュー (IT1)
module Cargotracker.Shipper.Views.ShipperShowView
  ( shipperShowPage,
    shipperNotFoundPage,
  ) where

import qualified Data.Text as T
import Lucid

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank (..),
    CorporateNumber (..),
    Shipper (..),
    ShipperKind (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (Address (..))
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail
  ( ContactEmail (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (ShipperName (..))

shipperShowPage :: Shipper -> Html ()
shipperShowPage s = pageLayout "荷主詳細 - Cargo Tracker" $ do
  let ShipperId sid = shipperId s
      ShipperName nm = shipperName s
      ContactEmail em = shipperEmail s
      Address addr = shipperAddress s
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] (toHtml ("荷主詳細: " <> sid))
      flashAlert FlashSuccess "登録しました"
      table_ [class_ "table"] $ tbody_ $ do
        tr_ $ do
          th_ "荷主 ID"
          td_ (toHtml sid)
        tr_ $ do
          th_ "氏名 / 社名"
          td_ (toHtml nm)
        tr_ $ do
          th_ "メール"
          td_ (toHtml em)
        tr_ $ do
          th_ "住所"
          td_ (toHtml addr)
        case shipperKind s of
          Individual -> tr_ $ do
            th_ "種別"
            td_ "個人 (US02)"
          Corporate (CorporateNumber cn) rank -> do
            tr_ $ do
              th_ "種別"
              td_ "法人 (US03)"
            tr_ $ do
              th_ "法人番号"
              td_ (toHtml cn)
            tr_ $ do
              th_ "契約ランク"
              td_ (toHtml (T.pack (show rank)))
      a_ [href_ "/shippers/new", class_ "btn btn-secondary me-2"] "もう 1 件登録"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"

shipperNotFoundPage :: Html ()
shipperNotFoundPage = pageLayout "Not Found - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "荷主が見つかりません"
      flashAlert FlashDanger "指定された荷主 ID は存在しません"
      a_ [href_ "/", class_ "btn btn-primary"] "トップへ"
