{-# LANGUAGE PatternSynonyms #-}

{- | 経路を確定するコマンド (US09, IT4)

業務フロー:
1. BookingId + ItineraryId + Leg 一覧を受け取る
2. 既存予約を取得 (なければ BookingNotFound)
3. Domain で Itinerary を構築 (接続性 + 時刻 + 連番を検証)
4. ItineraryRepository.saveItinerary で永続化
5. Cargo.linkRoute で RouteProposed → RouteAssigned に遷移
6. BookingRepository.updateBooking で永続化
7. 成功なら確定済 Itinerary を返す

T-01 規約: Tx 境界は Application 層が張る (本実装は port 経由のため
具体的な Tx 制御は Infrastructure 層に委譲)。
T-02 規約: Repository は IO のみ。
T-03 規約: mkItinerary は純粋関数。

ADR-0008 (Itinerary + Leg 集約): 1 Tx で itinerary + leg を保存し、
booking の status と itinerary_id 紐付けも同 Tx で更新する。
本イテレーション段階では Itinerary 永続化と Cargo 更新を別 port 呼び出しで行い、
Phase C で 1 つのトランザクションに束ねる移行を行う。
-}
module Cargotracker.Booking.Application.ConfirmRouteCommand
  ( ConfirmRouteInput (..),
    ConfirmRouteResult (..),
    execute,
  ) where

import Data.List.NonEmpty (NonEmpty)

import Cargotracker.Booking.Application.ItineraryPorts
  ( ItineraryRepository (..),
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    linkRoute,
  )
import Cargotracker.Booking.Domain.Model.Itinerary
  ( Itinerary,
    mkItinerary,
  )
import Cargotracker.Booking.Domain.Model.Leg (Leg)
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
import Cargotracker.Booking.Domain.Model.Value.ItineraryId (ItineraryId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data ConfirmRouteInput = ConfirmRouteInput
  { inputBookingId :: !BookingId
  , inputItineraryId :: !ItineraryId
  , inputLegs :: !(NonEmpty Leg)
  }
  deriving stock (Eq, Show)

data ConfirmRouteResult = ConfirmRouteResult
  { resultCargo :: !Cargo
  , resultItinerary :: !Itinerary
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  ItineraryRepository m ->
  ConfirmRouteInput ->
  m (Either DomainError ConfirmRouteResult)
execute bookingRepo itineraryRepo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById bookingRepo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case mkItinerary (inputItineraryId input) (inputLegs input) of
      Left e -> pure (Left e)
      Right itinerary -> do
        -- 1. Cargo 状態遷移を先に試行 (RouteProposed 以外は早期 fail)
        case linkRoute cargo of
          Left e -> pure (Left e)
          Right updatedCargo -> do
            -- 2. Itinerary を保存
            saveResult <- saveItinerary itineraryRepo bid itinerary
            case saveResult of
              Left e -> pure (Left e)
              Right () -> do
                -- 3. Cargo を保存 (経路紐付け済の状態に更新)
                updateResult <- updateBooking bookingRepo updatedCargo
                case updateResult of
                  Left e -> pure (Left e)
                  Right () ->
                    pure
                      ( Right
                          ConfirmRouteResult
                            { resultCargo = updatedCargo
                            , resultItinerary = itinerary
                            }
                      )
