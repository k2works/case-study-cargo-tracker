{-# LANGUAGE DataKinds #-}
{-# LANGUAGE TypeOperators #-}

-- | GET /login の HTML ページを返す Servant API (IT1)
module Cargotracker.Shared.Auth.Interfaces.LoginPageApi
  ( LoginPageApi,
    loginPageApp,
  ) where

import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Shared.Auth.Views.LoginView (loginPage)

type LoginPageApi = "login" :> Get '[HTML] (Html ())

loginPageApp :: Application
loginPageApp =
  serve (Proxy :: Proxy LoginPageApi) (pure (loginPage Nothing))
