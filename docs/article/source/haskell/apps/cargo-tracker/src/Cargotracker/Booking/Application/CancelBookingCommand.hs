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
    BookingDepartureContext (..),
    departureFromMaybe,
    departureToMaybe,
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

{- | 予約キャンセル料算定における出航日時コンテキスト (H-05 / T4-05 反映)

IT4 code review で「`Maybe UTCTime` は Confirmed 予約か否かの意図が曖昧」と指摘され、
IT5 (task 3.4 / T4-05) で明示的な sum type に移行した。

- `HasDeparture t`: Itinerary 紐付済で出航日時が確定 → CancellationPolicy で 3 段階算定
- `NoDeparture`  : Itinerary 未紐付 / 未確定 / Free 扱い

`departureFromMaybe` / `departureToMaybe` で後方互換の変換関数を提供する。
新規呼出は sum type コンストラクタを直接使うこと。
-}
data BookingDepartureContext
  = HasDeparture !UTCTime
  | NoDeparture
  deriving stock (Eq, Show)

-- | 既存 `Maybe UTCTime` 呼出との橋渡し (後方互換)。
departureFromMaybe :: Maybe UTCTime -> BookingDepartureContext
departureFromMaybe = maybe NoDeparture HasDeparture

-- | 逆方向の変換 (テスト / 永続化補助)。
departureToMaybe :: BookingDepartureContext -> Maybe UTCTime
departureToMaybe (HasDeparture t) = Just t
departureToMaybe NoDeparture = Nothing

data CancelBookingInput = CancelBookingInput
  { inputBookingId :: !BookingId
  , inputNow :: !UTCTime
  , inputDepartureTime :: !BookingDepartureContext
  -- ^ H-05 (T4-05, IT5): `Maybe UTCTime` から sum type へ移行
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
  (Confirmed, HasDeparture dep) -> Policy.calculate (inputNow input) dep
  _ -> freeFee (inputNow input)
  where
    freeFee now =
      CancellationFee {cfTier = Free, cfRate = 0, cfCalculatedAt = now}
