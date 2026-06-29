{-# LANGUAGE OverloadedStrings #-}

-- | Layout のロール別メニュー出し分けのテスト (U-07, IT3)
module Shared.Web.LayoutSpec (spec) where

import Test.Hspec

import Cargotracker.Shared.Auth.Domain.User (Role (..))
import Cargotracker.Shared.Web.Layout
  ( NavMenuItem (..),
    menuItemsForRole,
  )

hrefs :: [NavMenuItem] -> [String]
hrefs = map (show . navHref)

spec :: Spec
spec = describe "menuItemsForRole (U-07)" $ do
  it "未認証は見積作成 + 航海検索 + Login のみ (H-01: 業務一覧は非露出)" $ do
    let items = menuItemsForRole Nothing
    map navHref items
      `shouldBe` [ "/estimates/new"
                 , "/voyages/search"
                 , "/login"
                 ]

  it "Sales は見積作成/見積一覧/荷主/予約一覧/予約登録/航海検索" $ do
    let items = menuItemsForRole (Just Sales)
    map navHref items
      `shouldBe` [ "/estimates/new"
                 , "/estimates"
                 , "/shippers"
                 , "/bookings"
                 , "/bookings/new"
                 , "/voyages/search"
                 ]

  it "Router は予約一覧/航海一覧/航海検索" $ do
    let items = menuItemsForRole (Just Router)
    map navHref items
      `shouldBe` ["/bookings", "/voyages", "/voyages/search"]

  it "MasterAdmin はマスタ全メニュー + 航海検索" $ do
    let items = menuItemsForRole (Just MasterAdmin)
    map navHref items
      `shouldBe` [ "/shippers"
                 , "/bookings"
                 , "/voyages"
                 , "/voyages/new"
                 , "/voyages/search"
                 ]

  it "Sales は航海登録を表示しない (MasterAdmin 専管)" $ do
    let salesHrefs = map navHref (menuItemsForRole (Just Sales))
    notElem "/voyages/new" salesHrefs `shouldBe` True

  it "Router は荷主一覧を表示しない (Sales/MasterAdmin 専管)" $ do
    let routerHrefs = map navHref (menuItemsForRole (Just Router))
    notElem "/shippers" routerHrefs `shouldBe` True

  it "全 8 ロールは少なくとも 1 件のメニューを持つ" $
    mapM_
      (\r -> length (menuItemsForRole (Just r)) `shouldSatisfy` (> 0))
      [Shipper, Consignee, Sales, Router, Tracker, Handler, Accountant, MasterAdmin]
