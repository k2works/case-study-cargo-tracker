{- | Exception BC の重要度 VO (US19/US20, IT7)

例外の重要度を 4 段階の順序付き列挙型で表現する。
比較可能 (Ord) で、通知配信の優先度判定・ダッシュボード表示のフィルタに使用する。

Low < Medium < High < Critical
-}
module Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( Level (..),
    ExceptionSeverity (..),
    levelToText,
    textToLevel,
  ) where

import Data.Text (Text)

-- | 重要度レベル (Low < Medium < High < Critical)
data Level
  = Low
  | Medium
  | High
  | Critical
  deriving stock (Eq, Show, Ord, Enum, Bounded)

-- | ExceptionSeverity VO (Level を単純にラップ、将来拡張余地)
newtype ExceptionSeverity = ExceptionSeverity {unSeverity :: Level}
  deriving newtype (Eq, Show, Ord)

-- | DB 保存用 Text 表現 (data-model.md §exception_record CHECK 制約と整合)
levelToText :: Level -> Text
levelToText Low = "LOW"
levelToText Medium = "MEDIUM"
levelToText High = "HIGH"
levelToText Critical = "CRITICAL"

-- | Text から Level への逆変換 (不正値は Nothing)
textToLevel :: Text -> Maybe Level
textToLevel "LOW" = Just Low
textToLevel "MEDIUM" = Just Medium
textToLevel "HIGH" = Just High
textToLevel "CRITICAL" = Just Critical
textToLevel _ = Nothing
