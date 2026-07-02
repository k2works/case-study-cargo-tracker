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
  | -- | 確認コードの有効期限切れ (T5-11, IT6)
    ConfirmationCodeExpired
  | -- IT5 追加 (US14 追跡番号)

    -- | 追跡番号の形式が不正 (8 文字英数大文字以外)
    InvalidTrackingNumberFormat !Text
  | -- | 追跡活動が存在しない (US18 追跡照会 404 用)
    TrackingNotFound !Text
  | -- IT5 追加 (US15 荷役登録)

    -- | HandlingType 文字列が不正 (RECEIVE/LOAD/UNLOAD/CUSTOMS/CLAIM 以外)
    InvalidHandlingType !Text
  | -- | 発生日時が未来 (現在時刻より後)
    HandlingEventTimeInFuture
  | -- | 予約 (Cargo) が見つからない (荷役登録の前提条件)
    HandlingBookingNotFound !Text
  | -- IT6 追加 (US21 輸送料金算出、Pricing BC)

    -- | 通貨コードが ISO 4217 の 3 文字大文字でない (Currency)
    InvalidCurrency !Text
  | -- | 金額が負値、または演算結果が負値になる (Cost)
    InvalidCost !Integer
  | -- | 異通貨同士の演算 (CurrencyMismatch left right)
    CurrencyMismatch !Text !Text
  | -- | 割引率が 0-100 の範囲外 (Discount)
    InvalidDiscountRate !Integer
  | -- | 通貨レートの有効期間が不正 (validFrom >= validTo)
    InvalidCurrencyRatePeriod
  | -- | 通貨レートが有効期限外 (期間開始前 or 期限切れ)
    CurrencyRateExpired
  | {- | 指定通貨の PricingRule が存在しない (US21 Application)。
    通貨コードは Text で保持し Shared が Pricing BC 型に依存しないようにする
    -}
    PricingRuleNotFound !Text
  | {- | from → to の有効な通貨レートが存在しない (US21 Application)。
    通貨コードは Text で保持する
    -}
    CurrencyRateNotFound !Text !Text
  deriving stock (Eq, Show)
