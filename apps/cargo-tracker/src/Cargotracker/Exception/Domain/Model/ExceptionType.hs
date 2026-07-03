{- | 例外種別 sum type (US19/US20, IT7)

Exception BC の 3 種類の例外詳細を統一的に扱う sum type。
DelayException / DamageException / LossException の 3 コンストラクタで
ExceptionRecord 集約が保持する `erType` 属性の型。

DB 永続化時は exception_type カラム (VARCHAR 'DELAY' / 'DAMAGE' / 'LOSS') と
detail_json (JSONB) の 2 段構成にマップする (ADR-0014、iteration_plan-7.md §DB マイグレーション)。
-}
module Cargotracker.Exception.Domain.Model.ExceptionType
  ( ExceptionType (..),
    exceptionTypeToText,
  ) where

import Data.Text (Text)

import Cargotracker.Exception.Domain.Model.DamageException (DamageException)
import Cargotracker.Exception.Domain.Model.DelayException (DelayException)
import Cargotracker.Exception.Domain.Model.LossException (LossException)

data ExceptionType
  = Delay !DelayException
  | Damage !DamageException
  | Loss !LossException
  deriving stock (Eq, Show)

-- | DB CHECK 制約用の Text 表現 (exception_record.exception_type)
exceptionTypeToText :: ExceptionType -> Text
exceptionTypeToText (Delay _) = "DELAY"
exceptionTypeToText (Damage _) = "DAMAGE"
exceptionTypeToText (Loss _) = "LOSS"
