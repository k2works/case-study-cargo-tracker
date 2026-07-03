{- | 例外記録集約ルート (US19/US20, IT7)

Exception BC の集約ルート。ExceptionType (Delay/Damage/Loss)、Severity、
Reporter、時刻情報を統合して 1 レコードとして扱う。

追跡番号は Text (Cross-BC 参照は Text-DTO、Rule 4 準拠)。
resolvedAt は Maybe UTCTime で未解決 (Nothing) / 解決済 (Just t) を表現。

domain-model.md §Exception / iteration_plan-7.md §5.1 に対応。
-}
module Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord (..),
    mkExceptionRecord,
    resolveException,
    isResolved,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Exception.Domain.Model.ExceptionSeverity (ExceptionSeverity)
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType)
import Cargotracker.Exception.Domain.Model.Reporter (Reporter)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data ExceptionRecord = ExceptionRecord
  { erExceptionId :: !Text
  -- ^ UUID Text (Application 層で採番)
  , erTrackingNumber :: !Text
  -- ^ 追跡番号 Text (Cross-BC 参照 = Rule 4)
  , erType :: !ExceptionType
  , erSeverity :: !ExceptionSeverity
  , erReporter :: !Reporter
  , erReportedAt :: !UTCTime
  , erResolvedAt :: !(Maybe UTCTime)
  }
  deriving stock (Eq, Show)

{- | ExceptionRecord のスマートコンストラクタ。

* exceptionId が trim 後空文字 → InvalidExceptionReason "empty exception id"
* trackingNumber が trim 後空文字 → InvalidExceptionReason "empty tracking number"

未解決状態 (erResolvedAt = Nothing) で構築する。解決は resolveException を呼ぶ。
-}
mkExceptionRecord ::
  Text ->
  ExceptionType ->
  ExceptionSeverity ->
  Reporter ->
  UTCTime ->
  Text ->
  Either DomainError ExceptionRecord
mkExceptionRecord exceptionId exceptionType severity reporter reportedAt trackingNumber
  | T.null (T.strip exceptionId) =
      Left (InvalidExceptionReason "empty exception id")
  | T.null (T.strip trackingNumber) =
      Left (InvalidExceptionReason "empty tracking number")
  | otherwise =
      Right
        ExceptionRecord
          { erExceptionId = T.strip exceptionId
          , erTrackingNumber = T.strip trackingNumber
          , erType = exceptionType
          , erSeverity = severity
          , erReporter = reporter
          , erReportedAt = reportedAt
          , erResolvedAt = Nothing
          }

{- | 例外を解決済にする (US19/US20 解決記録)。

未解決 (erResolvedAt = Nothing) → 解決済 (erResolvedAt = Just now)。
既に解決済の場合は ExceptionAlreadyResolved を返す (冪等性なし、
明示的エラー)。
-}
resolveException :: UTCTime -> ExceptionRecord -> Either DomainError ExceptionRecord
resolveException now er
  | isResolved er = Left ExceptionAlreadyResolved
  | otherwise = Right (er {erResolvedAt = Just now})

-- | 解決済かどうかを判定する述語。
isResolved :: ExceptionRecord -> Bool
isResolved er = case erResolvedAt er of
  Just _ -> True
  Nothing -> False
