{-# LANGUAGE PatternSynonyms #-}

{- | 予約を確定送信するコマンド (US06 補完, IT3 / H-03)

業務フロー:
1. BookingId を受け取り、BookingRepository.findCargoById で既存予約を取得
2. Domain の Cargo.submitBooking で Draft → Submitted の状態遷移を試行
3. 成功なら BookingRepository.updateBooking で永続化
4. 失敗 (対象不在 / 状態不正 / 楽観ロック衝突) は DomainError で呼び出し元に伝播

HandOverToRouterCommand と対称的な設計。Draft 状態の予約は一度 Submit して
からでないと経路設計者に引き渡せないため、UI フロー (US04 登録 → US06 引き渡し)
の中間ステップとして必須。

ADR-0005 (BCE-03): エラーは Booking.Domain.Error 経由のパターンで返す。
-}
module Cargotracker.Booking.Application.SubmitBookingCommand
  ( SubmitBookingInput (..),
    execute,
  ) where

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
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
execute repo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case submitBooking cargo of
      Left e -> pure (Left e)
      Right updated -> do
        result <- updateBooking repo updated
        case result of
          Left e -> pure (Left e)
          Right () -> pure (Right updated)
