{- | ドメインエラー共有カーネル

全 Bounded Context の検証エラーをこの sum type に集約する。
詳細は docs/design/domain-model.md (ドメインエラー節) と
iteration_plan-1.md エラー処理戦略を参照。
-}
module Cargotracker.Shared.Domain.DomainError
  ( DomainError (..),
  ) where

import Data.Text (Text)

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
  | -- IT2 追加

    -- | from / to の状態名を保持する状態遷移違反
    InvalidStateTransition !Text !Text
  deriving stock (Eq, Show)
