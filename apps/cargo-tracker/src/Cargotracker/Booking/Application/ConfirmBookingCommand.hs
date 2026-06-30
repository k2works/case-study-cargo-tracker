{-# LANGUAGE PatternSynonyms #-}

{- | 予約を確定するコマンド (US13, IT4)

業務フロー:
1. BookingId を受け取り BookingRepository.findCargoById で既存予約を取得
2. Domain の Cargo.confirmBooking で RouteAssigned → Confirmed への状態遷移を試行
3. 成功なら BookingRepository.updateBooking で永続化
4. 失敗 (対象不在 / 状態不正 / 楽観ロック衝突) は DomainError で呼び出し元に伝播

ADR-0005 (BCE-03): エラーは Booking.Domain.Error 経由のパターンで返す。
T-01 規約: トランザクション境界はインフラ層 (Repository 実装) で管理し、
本コマンドは Repository ポート経由でのみ I/O を行う。
-}
module Cargotracker.Booking.Application.ConfirmBookingCommand
  ( ConfirmBookingInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    confirmBooking,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
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
execute repo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case confirmBooking cargo of
      Left e -> pure (Left e)
      Right updated -> do
        result <- updateBooking repo updated
        case result of
          Left e -> pure (Left e)
          Right () -> pure (Right updated)
