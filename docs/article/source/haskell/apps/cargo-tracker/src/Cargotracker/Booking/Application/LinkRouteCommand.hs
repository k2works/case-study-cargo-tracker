{- | 確定経路を予約に紐付けるコマンド (US11, IT4)

`Cargo.linkRoute` で RouteProposed → RouteAssigned に遷移し、
成功なら BookingRepository.updateBooking で永続化する。
Itinerary の保存は ConfirmRouteCommand 側で実施。

M-01 リファクタ (IT4 レビュー): `withCargo` 共通ヘルパに集約。
-}
module Cargotracker.Booking.Application.LinkRouteCommand
  ( LinkRouteInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository,
    withCargo,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo,
    linkRoute,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

newtype LinkRouteInput = LinkRouteInput
  { inputBookingId :: BookingId
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  LinkRouteInput ->
  m (Either DomainError Cargo)
execute repo input = withCargo repo (inputBookingId input) linkRoute
