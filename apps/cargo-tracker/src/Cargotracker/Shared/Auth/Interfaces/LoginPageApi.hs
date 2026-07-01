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
import Data.Time (getCurrentTime)
import GHC.Generics (Generic)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Shared.Auth.Application.CreateSessionCommand
  ( CreateSessionInput (..),
    sessionTtlSeconds,
  )
import qualified Cargotracker.Shared.Auth.Application.CreateSessionCommand as CreateSession
import Cargotracker.Shared.Auth.Application.LoginCommand
  ( LoginInput (..),
    execute,
  )
import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier,
    UserRepository,
  )
import Cargotracker.Shared.Auth.Application.SessionPorts
  ( SessionRepository,
  )
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken (..))
import Cargotracker.Shared.Auth.Domain.User (Email (..))
import Cargotracker.Shared.Auth.Views.LoginView (loginPage)
import Cargotracker.Shared.Infrastructure.IdGenerator (generateSessionTokenText)

data LoginFormRequest = LoginFormRequest
  { email :: !Text
  , password :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type LoginPageApi =
  "login"
    :> ( Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] LoginFormRequest
             :> Verb
                  'POST
                  303
                  '[HTML]
                  ( Headers
                      '[ Header "Location" Text
                       , Header "Set-Cookie" Text
                       ]
                      NoContent
                  )
       )

loginPageApp ::
  UserRepository IO ->
  PasswordVerifier IO ->
  SessionRepository IO ->
  Application
loginPageApp repo verifier sessionRepo =
  serve
    (Proxy :: Proxy LoginPageApi)
    (handlerGet :<|> handlerPost repo verifier sessionRepo)

handlerGet :: Handler (Html ())
handlerGet = pure (loginPage Nothing)

handlerPost ::
  UserRepository IO ->
  PasswordVerifier IO ->
  SessionRepository IO ->
  LoginFormRequest ->
  Handler
    ( Headers
        '[ Header "Location" Text
         , Header "Set-Cookie" Text
         ]
        NoContent
    )
handlerPost repo verifier sessionRepo req = do
  let input =
        LoginInput
          { loginEmail = Email (email req)
          , loginPassword = password req
          }
  result <- liftIO (execute repo verifier input)
  case result of
    Left _ ->
      throwError $
        err401
          { errBody = "メールアドレスまたはパスワードが正しくありません"
          , errHeaders = [("Content-Type", "text/plain; charset=utf-8")]
          }
    Right _authUser -> do
      -- ADR-0010 準拠: ログイン成功時にセッション Cookie を発行
      now <- liftIO getCurrentTime
      tokenText <- liftIO generateSessionTokenText
      created <-
        liftIO
          ( CreateSession.execute
              sessionRepo
              CreateSessionInput
                { inputUsername = email req
                , -- \^ username 相当として email をそのまま使う (IT5 簡略)
                  inputTokenText = tokenText
                , inputNow = now
                }
          )
      case created of
        Left _ ->
          -- セッション発行失敗時も /login にリダイレクト (Cookie なし)
          pure
            ( addHeader
                "/login"
                (addHeader "" NoContent)
            )
        Right (SessionToken tok) ->
          pure
            ( addHeader
                "/"
                (addHeader (setCookieValue tok) NoContent)
            )
  where
    -- HttpOnly + Secure は本番でのみ、開発では Secure を外す (localhost で送信されるため)
    setCookieValue :: Text -> Text
    setCookieValue tok =
      T.concat
        [ "cargo_session="
        , tok
        , "; HttpOnly; SameSite=Lax; Path=/; Max-Age="
        , T.pack (show sessionTtlSeconds)
        ]
