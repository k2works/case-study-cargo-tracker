{- | 予約を確定送信するコマンド (US06 補完, IT3 / H-03)

`Cargo.submitBooking` で Draft → Submitted の状態遷移を試行し、
成功なら BookingRepository.updateBooking で永続化する。

M-01 リファクタ (IT4 レビュー): `withCargo` 共通ヘルパに集約。
ADR-0005 (BCE-03): エラーは Booking.Domain.Error 経由のパターンで返す。
-}
module Cargotracker.Booking.Application.SubmitBookingCommand
  ( SubmitBookingInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository,
    withCargo,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo,
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

newtype SubmitBookingInput = SubmitBookingInput
  { inputBookingId :: BookingId
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  SubmitBookingInput ->
  m (Either DomainError Cargo)
execute repo input = withCargo repo (inputBookingId input) submitBooking
