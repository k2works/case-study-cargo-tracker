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
          "IT1 で実装したのは AUTH (認証基盤) と Booking フローの最小版です。経路設計 (US07-09) は IT3、追跡 (US18) は IT5 で追加予定。"
      h2_ [class_ "h4 mb-3"] "機能メニュー"
      div_ [class_ "row g-3"] $ do
        menuCard "/login" "🔐 ログイン" "認証 (JWT/Cookie + RBAC)"
        menuCard "/shippers/new" "👤 荷主登録" "US02 個人 / US03 法人"
        menuCard "/bookings/new" "📦 貨物予約登録" "US04 出発港 / 到着港 / 期限"
        menuCard "/voyages/new" "🚢 航海登録" "US24 多区間スケジュール"
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
