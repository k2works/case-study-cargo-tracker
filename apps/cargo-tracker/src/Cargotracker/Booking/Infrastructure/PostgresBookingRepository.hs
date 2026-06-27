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
import Data.Time (UTCTime)
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
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))

newPostgresBookingRepository :: Connection -> BookingRepository IO
newPostgresBookingRepository conn =
  BookingRepository
    { saveBooking = saveCargo conn
    , findCargoById = \(BookingId bid) -> findCargo conn bid
    }

findCargo :: Connection -> Text -> IO (Maybe Cargo)
findCargo conn bid = do
  rows <-
    query
      conn
      "SELECT c.booking_id, s.shipper_id, c.origin_unlocode, c.destination_unlocode, \
      \        c.deadline, c.booking_status, c.version \
      \ FROM cargo c JOIN shipper s ON s.id = c.shipper_id \
      \ WHERE c.booking_id = ? LIMIT 1"
      (Only bid) ::
      IO [(Text, Text, Text, Text, UTCTime, Text, Int)]
  case rows of
    [(bidV, sidV, orig, dest, deadlineV, statusT, ver)] ->
      pure $
        Just
          Cargo
            { cargoBookingId = BookingId bidV
            , cargoShipperId = ShipperId sidV
            , cargoRouteSpec =
                RouteSpecification
                  { origin = UnLocode orig
                  , destination = UnLocode dest
                  , arrivalDeadline = deadlineV
                  }
            , cargoStatus = textToBookingStatus statusT
            , cargoVersion = ver
            }
    _ -> pure Nothing

textToBookingStatus :: Text -> BookingStatus
textToBookingStatus "Submitted" = Submitted
textToBookingStatus "RouteProposed" = RouteProposed
textToBookingStatus "Confirmed" = Confirmed
textToBookingStatus "Closed" = Closed
textToBookingStatus _ = Draft

-- T-01 (IT2): shipper サロゲートキー解決失敗を `error` で潰さず
-- `Left (ShipperNotFound ...)` を返して呼び出し元に伝播する。
saveCargo :: Connection -> Cargo -> IO (Either DomainError ())
saveCargo conn c = do
  let ShipperId sidBusiness = cargoShipperId c
  rows <-
    query
      conn
      "SELECT id FROM shipper WHERE shipper_id = ? LIMIT 1"
      (Only sidBusiness) ::
      IO [Only Int]
  case rows of
    [Only shipperPk] -> do
      insertCargo conn shipperPk c
      pure (Right ())
    _ -> pure (Left (ShipperNotFound sidBusiness))

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
