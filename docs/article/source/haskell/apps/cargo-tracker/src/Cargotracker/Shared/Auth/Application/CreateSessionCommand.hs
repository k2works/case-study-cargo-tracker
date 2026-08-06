{- | セッション発行コマンド (task 1.2, ADR-0010, IT5)

ログイン成功後に呼ばれる。トークン (乱数) は Application 呼出側で生成し、
本 Command は Domain 検証 + 保存を行う (T-03 準拠)。

有効期限: 8 時間 (ADR-0010)。sliding window は AuthHandler 側で touch する。
-}
module Cargotracker.Shared.Auth.Application.CreateSessionCommand
  ( CreateSessionInput (..),
    sessionTtlSeconds,
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime, addUTCTime, secondsToNominalDiffTime)

import Cargotracker.Shared.Auth.Application.SessionPorts
  ( SessionRepository (..),
  )
import Cargotracker.Shared.Auth.Domain.Session (Session (..))
import Cargotracker.Shared.Auth.Domain.SessionToken
  ( SessionToken,
    mkSessionToken,
  )
import Cargotracker.Shared.Auth.Domain.User (UserId (..))
import Cargotracker.Shared.Domain.DomainError (DomainError)

-- | セッション有効期限 (秒)。ADR-0010: 8 時間 = 28800 秒。
sessionTtlSeconds :: Integer
sessionTtlSeconds = 8 * 60 * 60

data CreateSessionInput = CreateSessionInput
  { inputUsername :: !Text
  , inputTokenText :: !Text
  -- ^ Application 呼出側で乱数生成 (base64url 44 文字)
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  SessionRepository m ->
  CreateSessionInput ->
  m (Either DomainError SessionToken)
execute repo input =
  case mkSessionToken (inputTokenText input) of
    Left err -> pure (Left err)
    Right tok -> do
      let session =
            Session
              { sessionToken = tok
              , sessionUserId = UserId (inputUsername input)
              , sessionExpiresAt =
                  addUTCTime
                    (secondsToNominalDiffTime (fromInteger sessionTtlSeconds))
                    (inputNow input)
              , sessionLastUsedAt = inputNow input
              }
      persist <- saveSession repo session
      case persist of
        Left err -> pure (Left err)
        Right () -> pure (Right tok)
