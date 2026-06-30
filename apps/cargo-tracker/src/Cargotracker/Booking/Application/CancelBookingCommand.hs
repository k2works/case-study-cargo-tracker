{-# LANGUAGE PatternSynonyms #-}

{- | 予約をキャンセルするコマンド (US13, IT4)

業務フロー:
1. BookingId + 現在時刻 (now) を受け取り、既存予約を取得
2. Confirmed 状態の場合のみ CancellationPolicy で出航日時から料金 (ティア + レート) を算定
3. Domain の Cargo.cancelBooking で状態を Cancelled へ遷移
4. 永続化し、算定したキャンセル料を結果として返す
   (Submitted / RouteProposed / RouteAssigned からは料金 0 / Free 扱い)

ADR-0007 (キャンセル料 3 段階ルール): 出航日時の参照には Itinerary が必要だが、
本イテレーション (IT4 Phase B 着手段階) では Itinerary 永続化が未実装のため、
Application 層で出航日時を引数で受け取る簡略インタフェースとする。
Phase C で Itinerary 永続化後に Repository から自動取得する設計へ移行する。

T-01 規約: トランザクション境界は Repository 実装内に閉じ込め、
本コマンドはポート経由でのみ I/O を行う。
T-03 規約: CancellationPolicy.calculate は純粋関数。
-}
module Cargotracker.Booking.Application.CancelBookingCommand
  ( CancelBookingInput (..),
    CancelBookingResult (..),
    execute,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    cancelBooking,
  )
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
  )
import qualified Cargotracker.Booking.Domain.Service.CancellationPolicy as Policy
import Cargotracker.Shared.Domain.DomainError (DomainError)

data CancelBookingInput = CancelBookingInput
  { inputBookingId :: !BookingId
  , inputNow :: !UTCTime
  , inputDepartureTime :: !(Maybe UTCTime)
  -- ^ Confirmed 予約のみ必要。それ以外は Nothing 可 (Free 扱い)
  }
  deriving stock (Eq, Show)

data CancelBookingResult = CancelBookingResult
  { resultCargo :: !Cargo
  , resultFee :: !CancellationFee
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  CancelBookingInput ->
  m (Either DomainError CancelBookingResult)
execute repo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo ->
      let fee = computeFee (cargoStatus cargo) input
       in case cancelBooking cargo of
            Left e -> pure (Left e)
            Right updated -> do
              persist <- updateBooking repo updated
              case persist of
                Left e -> pure (Left e)
                Right () ->
                  pure
                    ( Right
                        CancelBookingResult
                          { resultCargo = updated
                          , resultFee = fee
                          }
                    )

-- | 現状態と入力から料金を算定する。Confirmed 以外は Free。
computeFee :: BookingStatus -> CancelBookingInput -> CancellationFee
computeFee status input = case (status, inputDepartureTime input) of
  (Confirmed, Just dep) -> Policy.calculate (inputNow input) dep
  _ -> freeFee (inputNow input)
  where
    freeFee now =
      CancellationFee {cfTier = Free, cfRate = 0, cfCalculatedAt = now}
