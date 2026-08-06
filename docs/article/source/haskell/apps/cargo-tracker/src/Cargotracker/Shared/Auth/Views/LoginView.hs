{-# LANGUAGE OverloadedStrings #-}

-- | ログイン画面のビュー (IT1 AUTH)
module Cargotracker.Shared.Auth.Views.LoginView
  ( loginPage,
    loginResultPage,
  ) where

import Data.Text (Text)
import Lucid

import Cargotracker.Shared.Auth.Domain.User (Email (..))
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
            , value_ "admin@example.com"
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
            , value_ "password"
            ]
        button_ [type_ "submit", class_ "btn btn-primary w-100"] "ログイン"
      div_ [class_ "mt-4 small"] $ do
        p_ [class_ "text-muted mb-1"] "IT1 デモ用シードユーザー (共通パスワード: password)"
        ul_ [class_ "text-muted"] $ do
          li_ "admin@example.com (MasterAdmin)"
          li_ "sales@example.com (Sales)"
          li_ "router@example.com (Router)"
          li_ "tracker@example.com (Tracker)"
          li_ "handler@example.com (Handler)"
          li_ "accountant@example.com (Accountant)"
          li_ "shipper@example.com (Shipper)"
          li_ "consignee@example.com (Consignee)"

loginResultPage :: Email -> Text -> Html ()
loginResultPage (Email userEmail) roleText = pageLayout "ログイン成功 - Cargo Tracker" $
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-6"] $ do
      h1_ [class_ "h3 mb-4"] "ログイン成功"
      flashAlert FlashSuccess (userEmail <> " (" <> roleText <> ") としてログインしました")
      p_ [class_ "text-muted small"] "IT1: Cookie / セッション統合は IT2 で対応します"
      a_ [href_ "/", class_ "btn btn-primary"] "トップへ"
