{- | 破損例外記録コマンド (US20, IT7)

Exception BC の Application 層ユースケース。Text-DTO 入力から
DamageException → ExceptionRecord 集約を構築し Repository に永続化する。

RecordDelayExceptionCommand と同構造。Cross-BC 副作用 (Tracking 状態遷移
Damaged / Notification 発火) は次反復以降で Cross-BC ports 経由で追加する。
-}
module Cargotracker.Exception.Application.RecordDamageExceptionCommand
  ( RecordDamageExceptionInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.Amount (mkAmount)
import Cargotracker.Exception.Domain.Model.DamageException (mkDamageException)
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord,
    mkExceptionRecord,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    Level,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.Reporter (mkReporter)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data RecordDamageExceptionInput = RecordDamageExceptionInput
  { inputExceptionId :: !Text
  , inputTrackingNumber :: !Text
  , inputAmountValue :: !Integer
  , inputAmountCurrency :: !Text
  , inputDescription :: !Text
  , inputSeverityLevel :: !Level
  , inputReporterUserId :: !Text
  , inputReporterRole :: !Text
  , inputReportedAt :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  ExceptionRepository m ->
  RecordDamageExceptionInput ->
  m (Either DomainError ExceptionRecord)
execute repo input =
  case buildRecord input of
    Left err -> pure (Left err)
    Right record -> do
      saveResult <- saveException repo record
      case saveResult of
        Left err -> pure (Left err)
        Right () -> pure (Right record)

buildRecord :: RecordDamageExceptionInput -> Either DomainError ExceptionRecord
buildRecord input = do
  amount <- mkAmount (inputAmountValue input) (inputAmountCurrency input)
  damage <- mkDamageException amount (inputDescription input)
  reporter <- mkReporter (inputReporterUserId input) (inputReporterRole input)
  mkExceptionRecord
    (inputExceptionId input)
    (Damage damage)
    (ExceptionSeverity (inputSeverityLevel input))
    reporter
    (inputReportedAt input)
    (inputTrackingNumber input)
