{-# LANGUAGE OverloadedStrings #-}

{- | Cookie 認証 + RolePolicy を組み合わせるゲート (T6-09, IT7)

Servant handler で `requireRoleGate` を呼ぶだけで:

1. Cookie ヘッダから SessionRepository でユーザ解決
2. 未認証なら 401
3. 認証済みでも RolePolicy 述語が False なら 403
4. すべて通れば AuthenticatedUser を返す

呼出側は返された AuthenticatedUser の authRoles を使わず、
本ゲート内で判定を完結させる。ハンドラは業務処理に集中できる。
-}
module Cargotracker.Shared.Auth.Interfaces.RoleGate
  ( requireRoleGate,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import Data.Time (UTCTime)
import Servant

import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository)
import Cargotracker.Shared.Auth.Domain.User (Role, UserId)
import Cargotracker.Shared.Auth.Interfaces.SessionAuth
  ( AuthenticatedUser (..),
    resolveCookieUser,
  )

{- | Cookie 認証 + Role Policy ゲート。
第 4 引数の述語 (`[Role] -> Bool`) は `Cargotracker.Shared.Auth.Domain.RolePolicy`
の関数を想定。
-}
requireRoleGate ::
  SessionRepository IO ->
  (UserId -> IO [Role]) ->
  IO UTCTime ->
  ([Role] -> Bool) ->
  Maybe Text ->
  Handler AuthenticatedUser
requireRoleGate repo lookupRoles nowM policy mHeader = do
  now <- liftIO nowM
  mUser <- liftIO (resolveCookieUser repo lookupRoles mHeader now)
  case mUser of
    Nothing ->
      throwError err401 {errBody = "{\"error\":\"authentication required\"}"}
    Just u ->
      if policy (authRoles u)
        then pure u
        else throwError err403 {errBody = "{\"error\":\"insufficient role\"}"}
