{-# LANGUAGE PatternSynonyms #-}

{- | Booking Bounded Context 固有のドメインエラー (ADR-0005 Phase 1)

Shared.Domain.DomainError からの分離は段階的に進める。本モジュールが新規
追加するエラーは BookingError に置く方針 (IT3+)。既存の BookingNotFound /
InvalidStateTransition は Shared.DomainError から再公開する形で Booking
レイヤから参照しやすくする。

詳細は docs/adr/0005-bounded-context-error-types.md を参照。
-}
module Cargotracker.Booking.Domain.Error
  ( BookingError,
    pattern BookingNotFound,
    pattern InvalidStateTransition,
    toDomainError,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError)
import qualified Cargotracker.Shared.Domain.DomainError as Shared

{- | Booking BC のエラー型 (ADR-0005 BCE-01)。

Phase 1 では既存 Shared.DomainError のエイリアスとし、Phase 3 で
独立 sum type に分離する予定。
-}
type BookingError = DomainError

{- | 予約 (Cargo) が見つからない場合のパターン (BCE-01)。

ユビキタス言語: 予約 (Booking) は Cargo 集約の業務識別子。Shared 側の
同名コンストラクタとビット等価。Phase 3 で BookingError 独自定義に
切り替わる際もパターンとしての互換性を保つ。
-}
pattern BookingNotFound :: Text -> BookingError
pattern BookingNotFound bid = Shared.BookingNotFound bid

{- | Cargo の状態遷移違反 (BCE-01)。

from / to は Cargotracker.Booking.Domain.Model.State.BookingStatus の
コンストラクタ名を Show で文字列化したもの。
-}
pattern InvalidStateTransition :: Text -> Text -> BookingError
pattern InvalidStateTransition fromS toS = Shared.InvalidStateTransition fromS toS

{- | Booking 固有エラーを HTTP 境界の通貨型 (DomainError) へ lift する (BCE-03)。

Phase 1 では型エイリアスのため恒等関数。Phase 3 で独立 sum type に
切り替わった際に実質的な変換ロジックを持つ。
-}
toDomainError :: BookingError -> DomainError
toDomainError = id
