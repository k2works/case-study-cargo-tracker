{-# LANGUAGE PatternSynonyms #-}

{- | 経路紐付けを解除するコマンド (US11, IT4)

業務フロー:
1. BookingId を受け取り、既存予約 (RouteAssigned 状態) を取得
2. Domain の Cargo.unlinkRoute で RouteAssigned → Draft に遷移
3. 永続化
4. 失敗 (不在 / 確定済 / 楽観ロック) は DomainError で伝播

確定済 (Confirmed) からの解除は Domain 層で拒否される。
T-01/T-02/T-03 規約: 全ての I/O は BookingRepository ポート経由。
-}
module Cargotracker.Booking.Application.UnlinkRouteCommand
  ( UnlinkRouteInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    unlinkRoute,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
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
execute repo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case unlinkRoute cargo of
      Left e -> pure (Left e)
      Right updated -> do
        result <- updateBooking repo updated
        case result of
          Left e -> pure (Left e)
          Right () -> pure (Right updated)
