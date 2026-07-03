{- | 遅延例外記録コマンド (US19, IT7)

Exception BC の Application 層ユースケース。Text-DTO 入力から
DelayException → ExceptionRecord 集約を構築し、Repository に永続化する。

フロー:

1. mkDelayException で遅延詳細を検証 (Domain 純粋)
2. mkReporter で報告者を検証
3. mkExceptionRecord で集約を構築 (未解決状態)
4. saveException で永続化

権限判定 (Handler/Tracker/Admin のみ) は Interfaces 層に委譲。
Cross-BC 副作用 (Tracking 状態遷移 / Notification 発火) は次反復以降で
Cross-BC ports 経由で追加する (ADR-0012 準拠、Tx 完了後に外出し)。
-}
module Cargotracker.Exception.Application.RecordDelayExceptionCommand
  ( RecordDelayExceptionInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.DelayException (mkDelayException)
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

data RecordDelayExceptionInput = RecordDelayExceptionInput
  { inputExceptionId :: !Text
  , inputTrackingNumber :: !Text
  , inputDelayHours :: !Int
  , inputReason :: !Text
  , inputSeverityLevel :: !Level
  , inputReporterUserId :: !Text
  , inputReporterRole :: !Text
  , inputReportedAt :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  ExceptionRepository m ->
  RecordDelayExceptionInput ->
  m (Either DomainError ExceptionRecord)
execute repo input =
  case buildRecord input of
    Left err -> pure (Left err)
    Right record -> do
      saveResult <- saveException repo record
      case saveResult of
        Left err -> pure (Left err)
        Right () -> pure (Right record)

buildRecord :: RecordDelayExceptionInput -> Either DomainError ExceptionRecord
buildRecord input = do
  delay <- mkDelayException (inputDelayHours input) (inputReason input)
  reporter <- mkReporter (inputReporterUserId input) (inputReporterRole input)
  mkExceptionRecord
    (inputExceptionId input)
    (Delay delay)
    (ExceptionSeverity (inputSeverityLevel input))
    reporter
    (inputReportedAt input)
    (inputTrackingNumber input)
