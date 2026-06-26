{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | ログイン画面 (GET + POST 両対応 / SSR)

GET  /login : フォーム表示
POST /login : FormUrlEncoded 受信 → LoginCommand 実行 → 結果ページ表示

JSON API (`POST /login` JSON) は LoginApi 側で提供する。Composition Root で
Content-Type に応じて分岐する。
-}
module Cargotracker.Shared.Auth.Interfaces.LoginPageApi
  ( LoginPageApi,
    loginPageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import qualified Data.Text as T
import GHC.Generics (Generic)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Shared.Auth.Application.LoginCommand
  ( LoginInput (..),
    execute,
  )
import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier,
    UserRepository,
  )
import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    User (..),
    userEmail,
    userRole,
  )
import Cargotracker.Shared.Auth.Views.LoginView (loginPage, loginResultPage)

data LoginFormRequest = LoginFormRequest
  { email :: !Text
  , password :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type LoginPageApi =
  "login"
    :> ( Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] LoginFormRequest :> Post '[HTML] (Html ())
       )

loginPageApp :: UserRepository IO -> PasswordVerifier IO -> Application
loginPageApp repo verifier =
  serve (Proxy :: Proxy LoginPageApi) (handlerGet :<|> handlerPost repo verifier)

handlerGet :: Handler (Html ())
handlerGet = pure (loginPage Nothing)

handlerPost ::
  UserRepository IO ->
  PasswordVerifier IO ->
  LoginFormRequest ->
  Handler (Html ())
handlerPost repo verifier req = do
  let input =
        LoginInput
          { loginEmail = Email (email req)
          , loginPassword = password req
          }
  result <- liftIO (execute repo verifier input)
  pure $ case result of
    Left _ ->
      loginPage (Just "メールアドレスまたはパスワードが正しくありません")
    Right user ->
      loginResultPage
        (userEmail user)
        (T.pack (show (userRole user)))
