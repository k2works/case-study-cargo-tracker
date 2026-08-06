{- | AUTH Application 層のポート (型クラス代わりの「レコード of 関数」)

ヘキサゴナルの精神に従い、副作用 (DB アクセス、bcrypt 検証) は
Application 層からポートとして抽象化する。テストではフェイクで
差し替え、本番では Infrastructure 層の実装を注入する。
-}
module Cargotracker.Shared.Auth.Application.Ports
  ( UserRepository (..),
    PasswordVerifier (..),
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Auth.Domain.User (Email, PasswordHash, User)

newtype UserRepository m = UserRepository
  { findByEmail :: Email -> m (Maybe User)
  }

newtype PasswordVerifier m = PasswordVerifier
  { verify :: Text -> PasswordHash -> m Bool
  }
