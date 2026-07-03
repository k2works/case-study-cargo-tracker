{- | 紛失例外記録コマンド (US20, IT7)

Exception BC の Application 層ユースケース。Text-DTO 入力から
LossException → ExceptionRecord 集約を構築し Repository に永続化する。

lastSeenAt は Maybe Text で受け取り、Nothing = 不明 / Just = UN/LOCODE 業務キー。
-}
module Cargotracker.Exception.Application.RecordLossExceptionCommand
  ( RecordLossExceptionInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.Amount (mkAmount)
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord,
    mkExceptionRecord,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    Level,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.LossException (mkLossException)
import Cargotracker.Exception.Domain.Model.Reporter (mkReporter)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data RecordLossExceptionInput = RecordLossExceptionInput
  { inputExceptionId :: !Text
  , inputTrackingNumber :: !Text
  , inputAmountValue :: !Integer
  , inputAmountCurrency :: !Text
  , inputLastSeenAt :: !(Maybe Text)
  , inputSeverityLevel :: !Level
  , inputReporterUserId :: !Text
  , inputReporterRole :: !Text
  , inputReportedAt :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  ExceptionRepository m ->
  -- | ADR-0014 Phase 2 Cross-BC helper
  (Text -> m (Either DomainError ())) ->
  RecordLossExceptionInput ->
  m (Either DomainError ExceptionRecord)
execute repo markInException input =
  case buildRecord input of
    Left err -> pure (Left err)
    Right record -> do
      transitionResult <- markInException (inputTrackingNumber input)
      case transitionResult of
        Left err -> pure (Left err)
        Right () -> do
          saveResult <- saveException repo record
          case saveResult of
            Left err -> pure (Left err)
            Right () -> pure (Right record)

buildRecord :: RecordLossExceptionInput -> Either DomainError ExceptionRecord
buildRecord input = do
  amount <- mkAmount (inputAmountValue input) (inputAmountCurrency input)
  loss <- mkLossException amount (inputLastSeenAt input)
  reporter <- mkReporter (inputReporterUserId input) (inputReporterRole input)
  mkExceptionRecord
    (inputExceptionId input)
    (Loss loss)
    (ExceptionSeverity (inputSeverityLevel input))
    reporter
    (inputReportedAt input)
    (inputTrackingNumber input)
