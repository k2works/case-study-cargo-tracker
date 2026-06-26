-- | ドメインエラー共有カーネル
--
-- 全 Bounded Context の検証エラーをこの sum type に集約する。
-- 詳細は docs/design/domain-model.md (ドメインエラー節) を参照。
--
-- IT1 で本格実装する。現時点は IT1 着手前の最小スタブとして
-- `Either DomainError a` パターンを型クラスで動作確認できる程度に留める。
module Cargotracker.Shared.Domain.DomainError
  ( DomainError (..),
  )
where

import           Data.Text (Text)

-- | ドメイン検証エラー (IT1-IT8 で順次拡張)
--
-- 完全な定義は domain-model.md を参照。本実装は IT1 で開始時の
-- 最小集合のみ含む。
data DomainError
  = -- | 予約 ID の形式不正 (BK-XXXXXX 形式)
    InvalidBookingId !Text
  | -- | UN/LOCODE 形式不正 (5 文字、先頭 2 文字大文字)
    InvalidUnLocode !Text
  | -- | 楽観ロック競合 (集約の version 不一致)
    ConcurrentModification !Text
  deriving stock (Eq, Show)
