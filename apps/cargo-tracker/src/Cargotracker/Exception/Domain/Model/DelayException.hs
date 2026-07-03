{- | 遅延例外 VO (US19, IT7)

Exception BC の遅延例外詳細。delayHours (正の Int) と reason (非空 Text) を保持する。
DamageException / LossException とは別の VO で、ExceptionType sum type の
コンストラクタ引数として使う。

data-model.md §exception_record.detail_json の型別詳細に対応。
-}
module Cargotracker.Exception.Domain.Model.DelayException
  ( DelayException (..),
    mkDelayException,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data DelayException = DelayException
  { deDelayHours :: !Int
  -- ^ 遅延時間 (正の整数、単位は時間)
  , deReason :: !Text
  -- ^ 遅延理由 (空文字不可、trim 後 500 文字以内)
  }
  deriving stock (Eq, Show)

{- | DelayException のスマートコンストラクタ。

* delayHours <= 0 → InvalidDelayHours
* reason が trim 後空文字 → InvalidExceptionReason "empty"
* reason が trim 後 500 文字超 → InvalidExceptionReason "too long"
-}
mkDelayException :: Int -> Text -> Either DomainError DelayException
mkDelayException hours reason
  | hours <= 0 = Left (InvalidDelayHours hours)
  | T.null trimmedReason = Left (InvalidExceptionReason "empty")
  | T.length trimmedReason > 500 = Left (InvalidExceptionReason "too long")
  | otherwise = Right (DelayException hours trimmedReason)
  where
    trimmedReason = T.strip reason
