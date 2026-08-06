{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | ログイン API (IT1 AUTH 1.4)

POST /login で {email, password} を受け取り、検証成功なら JWT を JSON で返す。
失敗 (検証不一致 / 未存在) は 401 Unauthorized。

IT2 で Cookie ベース (HttpOnly + SameSite=Strict) に拡張予定。
IT1 では JSON 返却で SPA / curl からの動作確認を容易にする。
-}
module Cargotracker.Shared.Auth.Interfaces.LoginApi
  ( LoginApi,
    LoginRequest (..),
    LoginResponse (..),
    loginApp,
    loginHandler,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Aeson (FromJSON, ToJSON)
import Data.Text (Text)
import GHC.Generics (Generic)
import Network.Wai (Application)
import Servant

import Cargotracker.Shared.Auth.Application.LoginCommand
  ( LoginInput (..),
    execute,
  )
import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier,
    UserRepository,
  )
import qualified Data.Text as T

import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    Role,
    User (..),
    userEmail,
    userId,
    userRole,
  )
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( Claims (..),
    JwtSecret,
    JwtTtlSeconds,
    computeExpiry,
    issue,
  )
import Data.Time.Clock.POSIX (POSIXTime)

data LoginRequest = LoginRequest
  { email :: !Text
  , password :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

data LoginResponse = LoginResponse
  { token :: !Text
  , role :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

type LoginApi =
  "login"
    :> ReqBody '[JSON] LoginRequest
    :> Post '[JSON] LoginResponse

loginHandler ::
  UserRepository IO ->
  PasswordVerifier IO ->
  JwtSecret ->
  JwtTtlSeconds ->
  IO POSIXTime ->
  LoginRequest ->
  Handler LoginResponse
loginHandler repo verifier secret ttl getNow req = do
  let input =
        LoginInput
          { loginEmail = Email (email req)
          , loginPassword = password req
          }
  result <- liftIO (execute repo verifier input)
  case result of
    Left _ -> throwError err401 {errBody = "{\"error\":\"invalid credentials\"}"}
    Right user -> do
      -- T-02 (IT2): exp を実時刻 + TTL から算出。固定の遠未来値を撤廃。
      now <- liftIO getNow
      let claims =
            Claims
              { claimsUserId = userId user
              , claimsEmail = userEmail user
              , claimsRole = userRole user
              , claimsExp = computeExpiry now ttl
              }
      tok <- liftIO (issue secret claims)
      pure
        LoginResponse
          { token = tok
          , role = roleToText (userRole user)
          }
  where
    roleToText :: Role -> Text
    roleToText = T.pack . show

loginApp ::
  UserRepository IO ->
  PasswordVerifier IO ->
  JwtSecret ->
  JwtTtlSeconds ->
  IO POSIXTime ->
  Application
loginApp repo verifier secret ttl getNow =
  serve (Proxy :: Proxy LoginApi) (loginHandler repo verifier secret ttl getNow)
