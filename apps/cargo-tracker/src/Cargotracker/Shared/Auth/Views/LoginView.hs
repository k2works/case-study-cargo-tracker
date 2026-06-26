{-# LANGUAGE OverloadedStrings #-}

-- | ログイン画面のビュー (IT1 AUTH)
module Cargotracker.Shared.Auth.Views.LoginView
  ( loginPage,
  ) where

import Data.Text (Text)
import Lucid

import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

loginPage :: Maybe Text -> Html ()
loginPage mError = pageLayout "Login - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-5"] $ do
      h1_ [class_ "h3 mb-4 text-center"] "Cargo Tracker ログイン"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      form_ [action_ "/login", method_ "post"] $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "email", class_ "form-label"] "メールアドレス"
          input_
            [ type_ "email"
            , id_ "email"
            , name_ "email"
            , class_ "form-control"
            , required_ "required"
            , autofocus_
            , autocomplete_ "username"
            ]
        div_ [class_ "mb-3"] $ do
          label_ [for_ "password", class_ "form-label"] "パスワード"
          input_
            [ type_ "password"
            , id_ "password"
            , name_ "password"
            , class_ "form-control"
            , required_ "required"
            , autocomplete_ "current-password"
            ]
        button_ [type_ "submit", class_ "btn btn-primary w-100"] "ログイン"
      p_
        [class_ "text-center mt-3 text-muted small"]
        "IT1: SSR ログイン画面 (JSON API は POST /login)"
