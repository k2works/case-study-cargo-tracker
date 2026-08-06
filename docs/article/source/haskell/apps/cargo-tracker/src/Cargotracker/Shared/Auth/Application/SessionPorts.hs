{- | セッション永続化ポート (task 1.2, IT5)

Servant AuthHandler が Cookie 値からセッションを解決するために使う。
T-02 準拠: Repository は IO のみで Tx 開始禁止。
-}
module Cargotracker.Shared.Auth.Application.SessionPorts
  ( SessionRepository (..),
  ) where

import Cargotracker.Shared.Auth.Domain.Session (Session)
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data SessionRepository m = SessionRepository
  { saveSession :: Session -> m (Either DomainError ())
  , findByToken :: SessionToken -> m (Maybe Session)
  , deleteByToken :: SessionToken -> m (Either DomainError ())
  , touchLastUsed :: SessionToken -> m (Either DomainError ())
  -- ^ sliding window の last_used_at を NOW() に更新
  }
