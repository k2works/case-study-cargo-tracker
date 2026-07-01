{- | ドメインエラー共有カーネル

全 Bounded Context の検証エラーをこの sum type に集約する。
詳細は docs/design/domain-model.md (ドメインエラー節) と
iteration_plan-1.md エラー処理戦略を参照。
-}
module Cargotracker.Shared.Domain.DomainError
  ( DomainError (..),
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

{- | ドメイン検証エラー (IT1 で導入する集合)

IT2 以降で `RouteNotSatisfied` などを追加する。
-}
data DomainError
  = -- Booking
    InvalidBookingId !Text
  | InvalidUnLocode !Text
  | ConcurrentModification !Text
  | -- Shared.Auth (IT1)
    InvalidUserId !Text
  | InvalidEmail !Text
  | InvalidPasswordHash !Text
  | InvalidCredentials
  | AccessDenied !Text
  | -- Routing (IT1)
    InvalidVoyageNumber !Text
  | LegContinuityViolation !Text
  | -- Shipper / Booking 関連 (IT1)
    InvalidShipperId !Text
  | ShipperNotFound !Text
  | -- IT2 追加 (ADR-0005 Phase 1: 段階移行中。Phase 3 で削除予定)

    {- | from / to の状態名を保持する状態遷移違反

    ADR-0005 (BCE-01): Booking BC 固有エラー。新規参照は
    'Cargotracker.Booking.Domain.Error' のパターン経由で行うこと。
    -}
    InvalidStateTransition !Text !Text
  | {- | 予約 (Cargo) が見つからない (BookingId 文字列を保持)

    ADR-0005 (BCE-01): Booking BC 固有エラー。新規参照は
    'Cargotracker.Booking.Domain.Error' のパターン経由で行うこと。
    -}
    BookingNotFound !Text
  | -- IT3 追加 (US07 航海検索)

    -- | 航海検索の出発期間が逆順 (from > to)
    InvalidSearchPeriod !UTCTime !UTCTime
  | -- | 航海検索の出発地と目的地が同一 (UnLocode を Text として保持)
    SameOriginDestination !Text
  | -- | HS コードが 6-10 桁の数字でない (US27)
    InvalidHsCode !Text
  | -- | 通関申告ステータス文字列が不正 (US27)
    InvalidDeclarationStatus !Text
  | -- | 通関業者名が不正 (US27)
    InvalidBrokerName !Text
  | -- IT4 追加 (US09 Itinerary / Leg)

    -- | Itinerary ID が UUID 形式でない (US09)
    InvalidItineraryId !Text
  | -- | 経路区間 (Leg) の load_time > unload_time など順序不正 (US09)
    InvalidLeg !Text
  | -- | Itinerary が 1 区間未満、または隣接 Leg の接続が不整合 (US09)
    InvalidItinerary !Text
  | -- IT5 追加 (US16 引取確認コード)

    -- | 確認コード形式不正 (6 桁数字以外)
    InvalidConfirmationCodeFormat !Text
  | -- | 確認コードが登録値と一致しない
    ConfirmationCodeMismatch
  | -- | 確認コードが既に使用済み
    ConfirmationCodeAlreadyUsed
  | -- | 確認コード試行回数の上限超過 (Int = 上限値)
    ConfirmationCodeMaxAttemptsExceeded !Int
  deriving stock (Eq, Show)
