{- | 例外解決コマンド (US19/US20, IT7)

Exception BC の Application 層ユースケース。exception_id 指定で例外を解決済に
遷移させる。Tracker/Admin のみが実行可能 (権限判定は Interfaces 層)。

フロー:
1. findExceptionById で対象例外を取得
2. Domain resolveException で解決済に遷移 (Nothing → Just now)
3. updateExceptionResolution で永続化
-}
module Cargotracker.Exception.Application.ResolveExceptionCommand
  ( ResolveExceptionInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord,
    resolveException,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data ResolveExceptionInput = ResolveExceptionInput
  { inputExceptionId :: !Text
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  ExceptionRepository m ->
  ResolveExceptionInput ->
  m (Either DomainError ExceptionRecord)
execute repo input = do
  mExisting <- findExceptionById repo (inputExceptionId input)
  case mExisting of
    Nothing -> pure (Left (InvalidExceptionReason "not found"))
    Just existing ->
      case resolveException (inputNow input) existing of
        Left err -> pure (Left err)
        Right updated -> do
          result <- updateExceptionResolution repo (inputExceptionId input) updated
          case result of
            Left err -> pure (Left err)
            Right () -> pure (Right updated)
