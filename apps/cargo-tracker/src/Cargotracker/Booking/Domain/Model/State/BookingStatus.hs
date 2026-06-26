{- | 予約状態遷移 (IT1 US04 ベース、IT2-IT4 で拡張)

5 値 sum type。IT1 では Draft / Submitted のみ実際に使用、
他は domain-model.md の整合性のため列挙。

遷移ルール (IT1 範囲):
- Draft → Submitted (submitBooking)
- Submitted → submit はエラー (CC は IT2 で扱う)
-}
module Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  ) where

data BookingStatus
  = Draft
  | Submitted
  | RouteProposed
  | Confirmed
  | Closed
  deriving stock (Eq, Show, Enum, Bounded)
