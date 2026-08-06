{- | Itinerary 永続化ポート (US09, IT4)

Booking BC の Itinerary / Leg を永続化するためのインタフェース。
Infrastructure 層で Postgres 実装を提供する (Phase C で実装予定)。

T-02 規約: Repository は IO のみで Tx 開始は禁止。Application 層が
withDbTransaction で Tx 境界を張る。
-}
module Cargotracker.Booking.Application.ItineraryPorts
  ( ItineraryRepository (..),
  ) where

import Cargotracker.Booking.Domain.Model.Itinerary (Itinerary)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Booking.Domain.Model.Value.ItineraryId (ItineraryId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data ItineraryRepository m = ItineraryRepository
  { saveItinerary :: BookingId -> Itinerary -> m (Either DomainError ())
  {- ^ 新規 Itinerary を保存。同 BookingId への重複保存は Application 層で
  事前チェックする (Cargo 状態が RouteAssigned 以降なら拒否)。
  -}
  , findItineraryByBookingId :: BookingId -> m (Maybe Itinerary)
  {- ^ BookingId で関連 Itinerary を検索 (Cargo.cargoStatus が RouteAssigned/
  Confirmed のときに値を返す)。
  -}
  , findItineraryById :: ItineraryId -> m (Maybe Itinerary)
  -- ^ ItineraryId 直接検索 (内部参照用)。
  }
