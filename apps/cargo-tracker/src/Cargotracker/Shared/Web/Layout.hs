{-# LANGUAGE OverloadedStrings #-}

{- | 共通レイアウト (IT1 Lucid SSR)

Bootstrap 5 を CDN から読み込んだ最小レイアウト。
iteration_plan-1.md のフロントエンドアーキテクチャに準拠。
-}
module Cargotracker.Shared.Web.Layout
  ( pageLayout,
    pageLayoutFor,
    flashAlert,
    flashAlertWithAction,
    FlashLevel (..),
    NavMenuItem (..),
    menuItemsForRole,
  ) where

import Data.Text (Text)
import Lucid

import Cargotracker.Shared.Auth.Domain.User (Role (..))

data FlashLevel
  = FlashSuccess
  | FlashWarning
  | FlashDanger
  deriving stock (Eq, Show)

flashLevelClass :: FlashLevel -> Text
flashLevelClass FlashSuccess = "alert-success"
flashLevelClass FlashWarning = "alert-warning"
flashLevelClass FlashDanger = "alert-danger"

flashAlert :: FlashLevel -> Text -> Html ()
flashAlert level msg =
  div_ [class_ ("alert " <> flashLevelClass level), role_ "alert"] (toHtml msg)

{- | M-08 (IT3): メッセージと一緒にアクションリンクを併置する alert。

楽観ロック衝突など「次に何をすべきか」が明確な業務エラー用。
flashAlert と同じ visual だが、Bootstrap の `.d-flex` で右端にボタンを配置する。
-}
flashAlertWithAction ::
  FlashLevel ->
  Text -> -- メッセージ本文
  Text -> -- アクションラベル (例: "最新を再読込")
  Text -> -- アクション URL (再読込先など)
  Html ()
flashAlertWithAction level msg actionLabel actionUrl =
  div_
    [ class_ ("alert " <> flashLevelClass level <> " d-flex justify-content-between align-items-center")
    , role_ "alert"
    ]
    $ do
      span_ (toHtml msg)
      a_
        [href_ actionUrl, class_ "btn btn-sm btn-outline-secondary ms-3"]
        (toHtml actionLabel)

-- | ナビメニュー項目 (U-07: ロール別表示制御)
data NavMenuItem = NavMenuItem
  { navHref :: !Text
  , navLabel :: !Text
  }
  deriving stock (Eq, Show)

{- | U-07 (IT3): ロール別のメニュー項目を返す純粋関数。

`Nothing` (未認証) は最小限のメニュー (見積 / 追跡 / Login)。
各ロールは domain-model.md のアクター責務に従う:

* Sales: 見積 / 荷主 / 予約 (US01-US06 主要業務)
* Router: 予約 / 航海 / 経路設計 (US07-US11)
* MasterAdmin: 全メニュー (US24/US25 マスタ管理)
* その他 (Shipper / Consignee / Tracker / Handler / Accountant) はそれぞれの業務メニュー
-}
menuItemsForRole :: Maybe Role -> [NavMenuItem]
menuItemsForRole Nothing =
  -- H-01 (2026-06-29 レビュー反映): 業務一覧 (荷主 / 予約 / 航海) は荷主名・運賃・
  -- 予約状況を含むため未認証で露出させない。認証後にロール別メニューで提供する。
  [ NavMenuItem "/estimates/new" "見積作成"
  , NavMenuItem "/voyages/search" "航海検索"
  , NavMenuItem "/login" "Login"
  ]
menuItemsForRole (Just Sales) =
  [ NavMenuItem "/estimates/new" "見積作成"
  , NavMenuItem "/estimates" "見積一覧"
  , NavMenuItem "/shippers" "荷主一覧"
  , NavMenuItem "/bookings" "貨物予約一覧"
  , NavMenuItem "/bookings/new" "予約登録"
  , NavMenuItem "/voyages/search" "航海検索"
  ]
menuItemsForRole (Just Router) =
  [ NavMenuItem "/bookings" "貨物予約一覧"
  , NavMenuItem "/voyages" "航海一覧"
  , NavMenuItem "/voyages/search" "航海検索"
  ]
menuItemsForRole (Just MasterAdmin) =
  [ NavMenuItem "/shippers" "荷主一覧"
  , NavMenuItem "/bookings" "貨物予約一覧"
  , NavMenuItem "/voyages" "航海一覧"
  , NavMenuItem "/voyages/new" "航海登録"
  , NavMenuItem "/voyages/search" "航海検索"
  ]
menuItemsForRole (Just Shipper) =
  [ NavMenuItem "/bookings" "貨物予約一覧"
  ]
menuItemsForRole (Just Tracker) =
  [ NavMenuItem "/bookings" "貨物予約一覧"
  ]
menuItemsForRole (Just Handler) =
  [ NavMenuItem "/bookings" "貨物予約一覧"
  ]
menuItemsForRole (Just Accountant) =
  [ NavMenuItem "/bookings" "貨物予約一覧"
  ]
menuItemsForRole (Just Consignee) =
  [ NavMenuItem "/bookings" "貨物予約一覧"
  ]

-- | 後方互換: ロール未指定 (Nothing) でレンダリング
pageLayout :: Text -> Html () -> Html ()
pageLayout = pageLayoutFor Nothing

{- | U-07 (IT3): ロールに応じたナビメニューでレイアウトを描画する。

`Nothing` を渡すと未認証 (最小メニュー)、`Just role` を渡すとそのロール用
メニューが表示される。各 PageApi ハンドラは将来的に Cookie/JWT からロールを
復元してこの関数を呼ぶ。Phase 1 (IT3 U-07) では既存ハンドラの大半が
`pageLayout` (Nothing) を呼んでおり、段階移行で Just role 化する。
-}
pageLayoutFor :: Maybe Role -> Text -> Html () -> Html ()
pageLayoutFor mRole title body = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    meta_ [name_ "viewport", content_ "width=device-width, initial-scale=1"]
    title_ (toHtml title)
    link_
      [ rel_ "stylesheet"
      , href_ "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
      ]
  body_ $ do
    nav_ [class_ "navbar navbar-expand-lg navbar-dark bg-primary"] $
      div_ [class_ "container-fluid"] $ do
        a_ [class_ "navbar-brand", href_ "/"] "Cargo Tracker"
        ul_ [class_ "navbar-nav me-auto"] $ do
          mapM_ renderItem (menuItemsForRole mRole)
          -- 認証済はログアウトを表示
          case mRole of
            Just _ ->
              li_ [class_ "nav-item"] $
                a_ [class_ "nav-link", href_ "/logout"] "ログアウト"
            Nothing -> mempty
          -- M-09 (IT3): Health は運用メニューのため業務ロールでは非表示、
          -- 未認証 (オペレータがログイン前にヘルスチェックする場面) と
          -- MasterAdmin (運用責務) のみで表示する。
          case mRole of
            Nothing ->
              li_ [class_ "nav-item"] $
                a_ [class_ "nav-link text-light-emphasis small", href_ "/health"] "Health"
            Just MasterAdmin ->
              li_ [class_ "nav-item"] $
                a_ [class_ "nav-link text-light-emphasis small", href_ "/health"] "Health"
            Just _ -> mempty
    main_ [class_ "container my-4"] body
    script_
      [ src_ "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"
      ]
      ("" :: Text)
    script_
      [ src_ "https://unpkg.com/htmx.org@1.9.12"
      ]
      ("" :: Text)
  where
    renderItem :: NavMenuItem -> Html ()
    renderItem (NavMenuItem href lbl) =
      li_ [class_ "nav-item"] $
        a_ [class_ "nav-link", href_ href] (toHtml lbl)
