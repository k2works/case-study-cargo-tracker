{- | Exception BC Application 層のポート (US19/US20, IT7)

`ExceptionRepository` を Application が依存する。Infrastructure 層で
PostgreSQL 実装を注入する (次反復以降で追加)。

T-02 準拠: Repository 関数は IO のみ。Tx 境界は Application が管理する。
-}
module Cargotracker.Exception.Application.Ports
  ( ExceptionRepository (..),
  ) where

import Data.Text (Text)

import Cargotracker.Exception.Domain.Model.ExceptionRecord (ExceptionRecord)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data ExceptionRepository m = ExceptionRepository
  { saveException :: ExceptionRecord -> m (Either DomainError ())
  -- ^ 新規例外レコードを永続化する (INSERT のみ、update は resolveException 後の別コマンド)
  , findExceptionById :: Text -> m (Maybe ExceptionRecord)
  -- ^ exceptionId (UUID Text) で 1 件検索
  , findExceptionsByTrackingNumber :: Text -> m [ExceptionRecord]
  -- ^ trackingNumber (Text-DTO) に紐づく例外一覧 (reportedAt 降順)
  , updateExceptionResolution :: Text -> ExceptionRecord -> m (Either DomainError ())
  -- ^ 解決済への更新 (resolveException 後の永続化。第 1 引数は exceptionId)
  }
