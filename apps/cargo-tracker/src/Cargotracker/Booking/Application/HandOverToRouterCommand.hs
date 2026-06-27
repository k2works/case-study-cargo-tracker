{- | 予約を経路設計者に引き渡すコマンド (US06, IT2)

業務フロー:
1. BookingId を受け取り、BookingRepository.findCargoById で既存予約を取得
2. Domain の Cargo.requestRouting で Submitted → RouteProposed の状態遷移を試行
3. 成功なら BookingRepository.updateBooking で永続化
4. 失敗 (対象不在 / 状態不正 / 楽観ロック衝突) は DomainError で呼び出し元に伝播

トランザクション境界 (ADR 0002 T-01) は Infrastructure 層の updateBooking が
`withTransaction` でラップする想定。Application 層は純粋な業務ロジックに集中する。

通知 (経路設計者へのアラート) は IT2 ではログ + DB 通知レコードのみで、
メール送信は IT5 で導入予定。本コマンドはトランザクション境界の外で発火する
ようにし、ADR 0002 T-03 (Event Publish はトランザクション外) を満たす設計とする。
-}
module Cargotracker.Booking.Application.HandOverToRouterCommand
  ( HandOverToRouterInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    requestRouting,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype HandOverToRouterInput = HandOverToRouterInput
  { inputBookingId :: BookingId
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  HandOverToRouterInput ->
  m (Either DomainError Cargo)
execute repo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing ->
      pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case requestRouting cargo of
      Left e -> pure (Left e)
      Right updated -> do
        result <- updateBooking repo updated
        case result of
          Left e -> pure (Left e)
          Right () -> pure (Right updated)
