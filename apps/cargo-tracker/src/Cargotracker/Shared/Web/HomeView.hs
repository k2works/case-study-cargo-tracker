{-# LANGUAGE DataKinds #-}
{-# LANGUAGE OverloadedStrings #-}

{- | ホーム画面 (IT1)

全 4 機能へのメニュー + IT1 デモシナリオの案内。
ロール別メニュー切替は IT2 で JWT 認証統合後に対応する。
-}
module Cargotracker.Shared.Web.HomeView
  ( homePage,
    homeApp,
  ) where

import Data.Text (Text)
import Lucid
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Shared.Web.Layout (pageLayout)

homePage :: Html ()
homePage = pageLayout "Cargo Tracker (Haskell 版)" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      div_ [class_ "p-5 mb-4 bg-light rounded-3"] $ do
        h1_ [class_ "display-5 fw-bold"] "Cargo Tracker"
        p_
          [class_ "lead"]
          "国際貨物輸送管理システム Haskell 版 (IT1)。DDD + ヘキサゴナル + CQRS 構成、PostgreSQL 永続化、Lucid SSR + Bootstrap 5 UI。"
        hr_ [class_ "my-4"]
        p_
          [class_ "small text-muted"]
          "IT3 までで認証基盤 + Booking / Routing / Estimation の主要フロー (US07 航海検索 / US08a 経路候補 / US27 通関) を実装済み。追跡 (US18) は IT5 で追加予定。"
      h2_ [class_ "h4 mb-3"] "登録メニュー"
      div_ [class_ "row g-3"] $ do
        menuCard "/login" "🔐 ログイン" "認証 (JWT/Cookie + RBAC)"
        menuCard "/shippers/new" "👤 荷主登録" "US02 個人 / US03 法人"
        menuCard "/bookings/new" "📦 貨物予約登録" "US04 出発港 / 到着港 / 期限"
        menuCard "/voyages/new" "🚢 航海登録" "US24 多区間スケジュール"
        menuCard "/estimates/new" "💴 見積作成" "US01 重量・体積から運賃見積"
      h2_ [class_ "h4 mt-5 mb-3"] "一覧・検索メニュー"
      div_ [class_ "row g-3"] $ do
        menuCard "/estimates" "💴 見積一覧" "登録済見積 + 候補一覧"
        menuCard "/shippers" "👥 荷主一覧" "登録済荷主の参照"
        menuCard "/bookings" "📋 貨物予約一覧" "予約状態 + 通関情報 (US27) へ"
        menuCard "/voyages" "🛳️ 航海一覧" "登録済航海スケジュール参照"
        menuCard "/voyages/search" "🔍 航海検索 (US07)" "出発地・到着地・期間で検索"
      -- H-01 (2026-07-02 レビュー #1 反映): 料金体系 (Pricing) と業務通知履歴
      -- (Notification) は未認証で露出させない。ログイン後にロール別 navbar
      -- (Sales/Accountant/MasterAdmin → 送料計算 / Handler/Shipper/Consignee/
      -- MasterAdmin → 通知一覧) から提供する。
      h2_ [class_ "h4 mt-5 mb-3"] "デモシナリオ"
      ol_ [class_ "list-group list-group-numbered"] $ do
        li_ [class_ "list-group-item"] $ do
          strong_ "荷主登録"
          " → 個人荷主 SHP-XXXXXX を作成"
        li_ [class_ "list-group-item"] $ do
          strong_ "航海登録"
          " → 区間連続性のある航海 (例: JPTYO → HKHKG → USNYC) を作成"
        li_ [class_ "list-group-item"] $ do
          strong_ "貨物予約登録"
          " → 荷主 ID + 出発港 + 到着港 + 期限で予約 (Draft 状態) 作成"
        li_ [class_ "list-group-item"] $ do
          strong_ "経路候補算出 (US08a)"
          " → 予約詳細から「経路候補」リンク (/bookings/:id/routes) を開く"
        li_ [class_ "list-group-item"] $ do
          strong_ "通関情報紐付け (US27)"
          " → 予約詳細から「通関情報を登録」(/bookings/:id/customs/edit) で HS コード等を保存"
      div_ [class_ "mt-4 text-muted small"] $ do
        p_ "API:"
        ul_ $ do
          li_ (code_ "GET /health")
          li_ (code_ "POST /login (JSON → JWT)")
          li_ (code_ "POST /shippers, /bookings, /voyages (JSON API)")

menuCard :: Text -> Html () -> Html () -> Html ()
menuCard url title desc =
  div_ [class_ "col-md-6"] $
    a_ [href_ url, class_ "text-decoration-none text-dark"] $
      div_ [class_ "card h-100"] $
        div_ [class_ "card-body"] $ do
          h5_ [class_ "card-title"] title
          p_ [class_ "card-text text-muted"] desc

-- Servant の GET / で HTML を返す Application
type HomeApi = Get '[HTML] (Html ())

homeApp :: Application
homeApp = serve (Proxy :: Proxy HomeApi) (pure homePage)
