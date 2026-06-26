{-# LANGUAGE OverloadedStrings #-}

{- | 共通レイアウト (IT1 Lucid SSR)

Bootstrap 5 を CDN から読み込んだ最小レイアウト。
iteration_plan-1.md のフロントエンドアーキテクチャに準拠。
-}
module Cargotracker.Shared.Web.Layout
  ( pageLayout,
    flashAlert,
    FlashLevel (..),
  ) where

import Data.Text (Text)
import Lucid

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

pageLayout :: Text -> Html () -> Html ()
pageLayout title body = doctypehtml_ $ do
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
          li_ [class_ "nav-item"] $
            a_ [class_ "nav-link", href_ "/login"] "Login"
          li_ [class_ "nav-item"] $
            a_ [class_ "nav-link", href_ "/health"] "Health"
    main_ [class_ "container my-4"] body
    script_
      [ src_ "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"
      ]
      ("" :: Text)
    script_
      [ src_ "https://unpkg.com/htmx.org@1.9.12"
      ]
      ("" :: Text)
