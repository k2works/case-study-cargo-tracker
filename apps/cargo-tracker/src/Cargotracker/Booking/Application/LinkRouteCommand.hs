{-# LANGUAGE PatternSynonyms #-}

{- | 確定経路を予約に紐付けるコマンド (US11, IT4)

業務フロー:
1. BookingId を受け取り、既存予約 (RouteProposed 状態) を取得
2. Domain の Cargo.linkRoute で RouteProposed → RouteAssigned に遷移
3. 永続化
4. 失敗 (不在 / 状態不正 / 楽観ロック) は DomainError で伝播

Phase B 段階では Itinerary の保存は別 Command (ConfirmRouteCommand) に分離し、
本コマンドは Cargo 集約の状態遷移のみを担う。
Phase C で Itinerary 永続化と一体化する。

T-01/T-02/T-03 規約: 全ての I/O は BookingRepository ポート経由。
-}
module Cargotracker.Booking.Application.LinkRouteCommand
  ( LinkRouteInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    linkRoute,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
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
execute repo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case linkRoute cargo of
      Left e -> pure (Left e)
      Right updated -> do
        result <- updateBooking repo updated
        case result of
          Left e -> pure (Left e)
          Right () -> pure (Right updated)
