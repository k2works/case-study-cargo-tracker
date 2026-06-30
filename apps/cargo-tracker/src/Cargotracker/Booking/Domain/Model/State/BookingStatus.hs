{- | 予約状態遷移 (IT1 US04 ベース、IT2-IT4 で拡張)

Cargo 集約の状態。値の追加履歴:

- IT1: Draft, Submitted (実際に使用)
- IT2: RouteProposed, Confirmed, Closed (列挙のみ、未使用)
- IT4: RouteAssigned, Cancelled (US09 / US13 経路紐付け・キャンセルで使用)

遷移ルール (IT1-IT4 範囲):

- Draft → Submitted (submitBooking, IT1)
- Submitted → RouteProposed (requestRouting / handOverToRouter, IT2)
- RouteProposed → RouteAssigned (linkRoute, US11 IT4)
- RouteAssigned → Confirmed (confirmBooking, US13 IT4)
- RouteAssigned → Draft (unlinkRoute, US11 IT4 確定前のみ)
- Confirmed → Cancelled (cancelBooking, US13 IT4 キャンセル料 3 段階適用)
- Submitted → Cancelled (cancelBooking, US13 IT4 経路紐付け前のキャンセル)
- それ以外の遷移は InvalidStateTransition

`canTransitionTo` で純粋関数として遷移可否を判定する。
-}
module Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
    canTransitionTo,
    cancellableStatuses,
    bookingStatusToText,
  ) where

import Data.Char (isAsciiUpper)
import Data.Text (Text)
import qualified Data.Text as T

data BookingStatus
  = Draft
  | Submitted
  | RouteProposed
  | RouteAssigned
  | Confirmed
  | Cancelled
  | Closed
  deriving stock (Eq, Show, Enum, Bounded)

{- | キャンセル可能状態の集合 (US13, IT4)

`canTransitionTo s Cancelled == True` となる s のリスト。
派生して `cancelBooking` 等が利用する業務概念。
-}
cancellableStatuses :: [BookingStatus]
cancellableStatuses = [Submitted, RouteProposed, RouteAssigned, Confirmed]

-- | 状態遷移可否 (純粋関数、IT4 までのルールを網羅、SSoT)
canTransitionTo :: BookingStatus -> BookingStatus -> Bool
canTransitionTo Draft Submitted = True
canTransitionTo Submitted RouteProposed = True
canTransitionTo Submitted Cancelled = True
canTransitionTo RouteProposed RouteAssigned = True
canTransitionTo RouteProposed Cancelled = True
canTransitionTo RouteAssigned Confirmed = True
canTransitionTo RouteAssigned Draft = True
canTransitionTo RouteAssigned Cancelled = True
canTransitionTo Confirmed Cancelled = True
canTransitionTo Confirmed Closed = True
canTransitionTo _ _ = False

-- | DB CHECK 制約と整合する大文字スネークケース表現
bookingStatusToText :: BookingStatus -> Text
bookingStatusToText = T.toUpper . camelToSnake . T.pack . show
  where
    camelToSnake t = case T.uncons t of
      Nothing -> t
      Just (c, rest) -> T.cons c (foldMap toSnake (T.unpack rest))
    toSnake c
      | isAsciiUpper c = T.pack ['_', c]
      | otherwise = T.singleton c
