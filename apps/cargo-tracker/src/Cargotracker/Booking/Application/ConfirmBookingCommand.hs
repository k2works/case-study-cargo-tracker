{- | 予約を確定するコマンド (US13, IT4)

`Cargo.confirmBooking` で RouteAssigned → Confirmed の状態遷移を試行し、
成功なら BookingRepository.updateBooking で永続化する。

M-01 リファクタ (IT4 レビュー): `withCargo` 共通ヘルパに集約し
execute を 1 行で表現。
-}
module Cargotracker.Booking.Application.ConfirmBookingCommand
  ( ConfirmBookingInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository,
    withCargo,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo,
    confirmBooking,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

newtype ConfirmBookingInput = ConfirmBookingInput
  { inputBookingId :: BookingId
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  ConfirmBookingInput ->
  m (Either DomainError Cargo)
execute repo input = withCargo repo (inputBookingId input) confirmBooking
