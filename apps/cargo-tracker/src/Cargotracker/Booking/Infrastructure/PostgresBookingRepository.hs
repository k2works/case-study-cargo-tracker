{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の BookingRepository (IT1 US04 3.4)

cargo テーブルへの保存を実装。shipper への参照は業務キー (shipper_id)
からサロゲートキー (shipper.id) への変換が必要なため、別途 SELECT で
解決する。実運用 (IT2 以降) では Application 層でショッパー集約を
あらかじめロードしてサロゲートキーを伴って渡す方が効率的。
-}
module Cargotracker.Booking.Infrastructure.PostgresBookingRepository
  ( newPostgresBookingRepository,
  ) where

import Data.Text (Text)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
  )

import Cargotracker.Booking.Application.Ports (BookingRepository (..))
import Cargotracker.Booking.Domain.Model.Cargo (Cargo (..))
import Cargotracker.Booking.Domain.Model.State.BookingStatus (BookingStatus (..))
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))

newPostgresBookingRepository :: Connection -> BookingRepository IO
newPostgresBookingRepository conn =
  BookingRepository
    { saveBooking = saveCargo conn
    }

saveCargo :: Connection -> Cargo -> IO ()
saveCargo conn c = do
  let ShipperId sidBusiness = cargoShipperId c
  rows <-
    query
      conn
      "SELECT id FROM shipper WHERE shipper_id = ? LIMIT 1"
      (Only sidBusiness) ::
      IO [Only Int]
  case rows of
    [Only shipperPk] -> insertCargo conn shipperPk c
    _ -> error ("PostgresBookingRepository: shipper not found: " <> show sidBusiness)

insertCargo :: Connection -> Int -> Cargo -> IO ()
insertCargo conn shipperPk c = do
  let BookingId bid = cargoBookingId c
      route = cargoRouteSpec c
      UnLocode origin = originLoc route
      UnLocode destination = destLoc route
      statusText = bookingStatusToText (cargoStatus c)
  _ <-
    execute
      conn
      "INSERT INTO cargo (booking_id, shipper_id, origin_unlocode, destination_unlocode, \
      \                  deadline, booking_status, version) \
      \ VALUES (?, ?, ?, ?, ?, ?, ?)"
      ( bid
      , shipperPk
      , origin
      , destination
      , arrivalDeadline route
      , statusText
      , cargoVersion c
      )
  pure ()
  where
    originLoc :: RouteSpecification -> UnLocode
    originLoc = origin
    destLoc :: RouteSpecification -> UnLocode
    destLoc = destination

bookingStatusToText :: BookingStatus -> Text
bookingStatusToText Draft = "Draft"
bookingStatusToText Submitted = "Submitted"
bookingStatusToText RouteProposed = "RouteProposed"
bookingStatusToText Confirmed = "Confirmed"
bookingStatusToText Closed = "Closed"
