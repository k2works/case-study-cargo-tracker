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
    query_,
  )
import Database.PostgreSQL.Simple.Types ((:.) (..))

import Cargotracker.Booking.Application.Ports (BookingRepository (..))
import Cargotracker.Booking.Domain.Model.Cargo (Cargo (..))
import Cargotracker.Booking.Domain.Model.State.BookingStatus (BookingStatus (..))
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.CargoType
  ( CargoType (..),
    cargoTypeToText,
  )
import Cargotracker.Booking.Domain.Model.Value.HazardousDeclaration
  ( HazardousDeclaration (..),
  )
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Domain.Model.Value.TemperatureRequirement
  ( TemperatureRequirement (..),
    TemperatureUnit (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))

newPostgresBookingRepository :: Connection -> BookingRepository IO
newPostgresBookingRepository conn =
  BookingRepository
    { saveBooking = saveCargo conn
    , findCargoById = \(BookingId bid) -> findCargo conn bid
    , updateBooking = updateCargo conn
    , findAllCargos = listCargos conn
    }

listCargos :: Connection -> IO [Cargo]
listCargos conn = do
  rows <-
    query_
      conn
      "SELECT c.booking_id, s.shipper_id, c.origin_unlocode, c.destination_unlocode, \
      \        c.deadline, c.booking_status, c.version, \
      \        c.cargo_type, c.hazardous_class, c.un_number, c.proper_shipping_name, \
      \        c.min_temperature, c.max_temperature, c.temperature_unit \
      \ FROM cargo c JOIN shipper s ON s.id = c.shipper_id \
      \ ORDER BY c.booking_id LIMIT 100" ::
      IO
        [ ( Text
          , Text
          , Text
          , Text
          , UTCTime
          , Text
          , Int
          , Text
          , Maybe Text
          , Maybe Text
          , Maybe Text
          , Maybe Double
          , Maybe Double
          , Maybe Text
          )
        ]
  pure
    [ Cargo
        { cargoBookingId = BookingId bidV
        , cargoShipperId = ShipperId sidV
        , cargoRouteSpec =
            RouteSpecification
              { origin = UnLocode orig
              , destination = UnLocode dest
              , arrivalDeadline = deadlineV
              }
        , cargoStatus = textToBookingStatus statusT
        , cargoType = textToCargoType ctypeT mHC mUN mPSN mMin mMax mUnit
        , cargoVersion = ver
        }
    | (bidV, sidV, orig, dest, deadlineV, statusT, ver, ctypeT, mHC, mUN, mPSN, mMin, mMax, mUnit) <- rows
    ]

findCargo :: Connection -> Text -> IO (Maybe Cargo)
findCargo conn bid = do
  rows <-
    query
      conn
      "SELECT c.booking_id, s.shipper_id, c.origin_unlocode, c.destination_unlocode, \
      \        c.deadline, c.booking_status, c.version, \
      \        c.cargo_type, c.hazardous_class, c.un_number, c.proper_shipping_name, \
      \        c.min_temperature, c.max_temperature, c.temperature_unit \
      \ FROM cargo c JOIN shipper s ON s.id = c.shipper_id \
      \ WHERE c.booking_id = ? LIMIT 1"
      (Only bid) ::
      IO
        [ ( Text
          , Text
          , Text
          , Text
          , UTCTime
          , Text
          , Int
          , Text
          , Maybe Text
          , Maybe Text
          , Maybe Text
          , Maybe Double
          , Maybe Double
          , Maybe Text
          )
        ]
  case rows of
    [(bidV, sidV, orig, dest, deadlineV, statusT, ver, ctypeT, mHC, mUN, mPSN, mMin, mMax, mUnit)] ->
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
            , cargoType = textToCargoType ctypeT mHC mUN mPSN mMin mMax mUnit
            , cargoVersion = ver
            }
    _ -> pure Nothing

-- DB 列値から CargoType を復元する。CHECK 制約で整合性が保たれている前提
-- (スマートコンストラクタを通さず直接構築)。不整合時は General にフォールバックする。
textToCargoType ::
  Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Double ->
  Maybe Double ->
  Maybe Text ->
  CargoType
textToCargoType "HAZARDOUS" (Just hc) (Just un) (Just psn) _ _ _ =
  Hazardous
    HazardousDeclaration
      { hazardousClass = hc
      , unNumber = un
      , properShippingName = psn
      }
textToCargoType "REFRIGERATED" _ _ _ (Just lo) (Just hi) (Just u) =
  Refrigerated
    TemperatureRequirement
      { minTemperature = lo
      , maxTemperature = hi
      , temperatureUnit = if u == "F" then Fahrenheit else Celsius
      }
textToCargoType _ _ _ _ _ _ _ = General

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
      ctype = cargoType c
      (cargoTypeT, mHC, mUN, mPSN, mMin, mMax, mUnit) = cargoTypeColumns ctype
  _ <-
    execute
      conn
      "INSERT INTO cargo \
      \ (booking_id, shipper_id, origin_unlocode, destination_unlocode, \
      \  deadline, booking_status, version, \
      \  cargo_type, hazardous_class, un_number, proper_shipping_name, \
      \  min_temperature, max_temperature, temperature_unit) \
      \ VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      ( ( bid
        , shipperPk
        , origin
        , destination
        , arrivalDeadline route
        , statusText
        , cargoVersion c
        )
          :. (cargoTypeT, mHC, mUN, mPSN, mMin, mMax, mUnit)
      )
  pure ()
  where
    originLoc :: RouteSpecification -> UnLocode
    originLoc = origin
    destLoc :: RouteSpecification -> UnLocode
    destLoc = destination

-- CargoType を DB の (cargo_type, 危険物 3 列, 冷凍 3 列) の 7-tuple に分解する。
cargoTypeColumns ::
  CargoType ->
  (Text, Maybe Text, Maybe Text, Maybe Text, Maybe Double, Maybe Double, Maybe Text)
cargoTypeColumns ctype = case ctype of
  General -> (cargoTypeToText General, Nothing, Nothing, Nothing, Nothing, Nothing, Nothing)
  Hazardous d ->
    ( cargoTypeToText (Hazardous d)
    , Just (hazardousClass d)
    , Just (unNumber d)
    , Just (properShippingName d)
    , Nothing
    , Nothing
    , Nothing
    )
  Refrigerated r ->
    ( cargoTypeToText (Refrigerated r)
    , Nothing
    , Nothing
    , Nothing
    , Just (minTemperature r)
    , Just (maxTemperature r)
    , Just (case temperatureUnit r of Celsius -> "C"; Fahrenheit -> "F")
    )

-- US06 (IT2): 既存 cargo の booking_status / version を楽観ロック付きで更新する。
-- 期待バージョン (cargoVersion - 1) と DB 上の version が一致しない場合は
-- 影響行が 0 件となり ConcurrentModification を返す。
updateCargo :: Connection -> Cargo -> IO (Either DomainError ())
updateCargo conn c = do
  let BookingId bid = cargoBookingId c
      statusText = bookingStatusToText (cargoStatus c)
      newVersion = cargoVersion c
      expectedVersion = newVersion - 1
  affected <-
    execute
      conn
      "UPDATE cargo SET booking_status = ?, version = ? \
      \ WHERE booking_id = ? AND version = ?"
      (statusText, newVersion, bid, expectedVersion)
  if affected == 1
    then pure (Right ())
    else pure (Left (ConcurrentModification bid))

bookingStatusToText :: BookingStatus -> Text
bookingStatusToText Draft = "Draft"
bookingStatusToText Submitted = "Submitted"
bookingStatusToText RouteProposed = "RouteProposed"
bookingStatusToText Confirmed = "Confirmed"
bookingStatusToText Closed = "Closed"
