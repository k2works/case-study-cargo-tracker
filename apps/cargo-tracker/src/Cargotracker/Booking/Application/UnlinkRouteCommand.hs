{- | 経路紐付けを解除するコマンド (US11, IT4)

`Cargo.unlinkRoute` で RouteAssigned → Draft に遷移し、
成功なら BookingRepository.updateBooking で永続化する。
確定済 (Confirmed) からは Domain 層で拒否される。

M-01 リファクタ (IT4 レビュー): `withCargo` 共通ヘルパに集約。
-}
module Cargotracker.Booking.Application.UnlinkRouteCommand
  ( UnlinkRouteInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository,
    withCargo,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo,
    unlinkRoute,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

newtype UnlinkRouteInput = UnlinkRouteInput
  { inputBookingId :: BookingId
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  UnlinkRouteInput ->
  m (Either DomainError Cargo)
execute repo input = withCargo repo (inputBookingId input) unlinkRoute
