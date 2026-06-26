{- | ログインコマンド (IT1 AUTH 1.2)

`UserRepository` でメールアドレスから User を引き、
`PasswordVerifier` でパスワードを検証する。
ヒット時は User を返し、未ヒット / 検証失敗時は `InvalidCredentials` を返す
(攻撃者にユーザー存在有無を漏らさないため両方とも同じエラーにする)。
-}
module Cargotracker.Shared.Auth.Application.LoginCommand
  ( LoginInput (..),
    execute,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier (..),
    UserRepository (..),
  )
import Cargotracker.Shared.Auth.Domain.User (Email, User, userPasswordHash)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data LoginInput = LoginInput
  { loginEmail :: !Email
  , loginPassword :: !Text
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  UserRepository m ->
  PasswordVerifier m ->
  LoginInput ->
  m (Either DomainError User)
execute repo verifier input = do
  mUser <- findByEmail repo (loginEmail input)
  case mUser of
    Nothing -> pure (Left InvalidCredentials)
    Just user -> do
      ok <- verify verifier (loginPassword input) (userPasswordHash user)
      pure $
        if ok
          then Right user
          else Left InvalidCredentials
