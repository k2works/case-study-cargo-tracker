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
import qualified Data.Text as T
import qualified Data.Text.Encoding as T
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
    query_,
  )
import Database.PostgreSQL.Simple.Types (Query (..), (:.) (..))
import System.IO (hPutStrLn, stderr)

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
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

newPostgresBookingRepository :: Connection -> BookingRepository IO
newPostgresBookingRepository conn =
  BookingRepository
    { saveBooking = saveCargo conn
    , findCargoById = \(BookingId bid) -> findCargo conn bid
    , updateBooking = updateCargo conn
    , findAllCargos = listCargos conn
    }

-- U-11 (IT3 / M-01): cargo SELECT の重複した 14-tuple 行型を一つにまとめる。
-- listCargos と findCargo で SELECT 列順を完全に揃え、変換も rowToCargo に集約する。
type CargoRow =
  ( Text -- booking_id
  , Text -- shipper_id (business key)
  , Text -- origin_unlocode
  , Text -- destination_unlocode
  , UTCTime -- deadline
  , Text -- booking_status
  , Int -- version
  , Text -- cargo_type
  , Maybe Text -- hazardous_class
  , Maybe Text -- un_number
  , Maybe Text -- proper_shipping_name
  , Maybe Double -- min_temperature
  , Maybe Double -- max_temperature
  , Maybe Text -- temperature_unit
  )

cargoSelectColumns :: Text
cargoSelectColumns =
  "c.booking_id, s.shipper_id, c.origin_unlocode, c.destination_unlocode, \
  \ c.deadline, c.booking_status, c.version, \
  \ c.cargo_type, c.hazardous_class, c.un_number, c.proper_shipping_name, \
  \ c.min_temperature, c.max_temperature, c.temperature_unit"

{- | rowToCargo は CargoRow を Cargo に変換する。
DB データ不整合 (例: HAZARDOUS だが hazardousClass が NULL) があれば
WARN ログを stderr に出して General にフォールバックする (M-02 改善)。
-}
rowToCargo :: CargoRow -> IO Cargo
rowToCargo
  (bidV, sidV, orig, dest, deadlineV, statusT, ver, ctypeT, mHC, mUN, mPSN, mMin, mMax, mUnit) = do
    cType <- case parseCargoType ctypeT mHC mUN mPSN mMin mMax mUnit of
      Right ct -> pure ct
      Left issue -> do
        hPutStrLn
          stderr
          ( "WARN PostgresBookingRepository: cargo_type 復元失敗 / "
              <> "booking_id="
              <> T.unpack bidV
              <> " / issue="
              <> show issue
              <> " (General にフォールバック)"
          )
        pure General
    pure
      Cargo
        { cargoBookingId = BookingId bidV
        , cargoShipperRef = ShipperRef sidV
        , cargoRouteSpec =
            RouteSpecification
              { origin = UnLocode orig
              , destination = UnLocode dest
              , arrivalDeadline = deadlineV
              }
        , cargoStatus = textToBookingStatus statusT
        , cargoType = cType
        , cargoVersion = ver
        }

listCargos :: Connection -> IO [Cargo]
listCargos conn = do
  rows <-
    query_
      conn
      ( "SELECT "
          <> Query (T.encodeUtf8 cargoSelectColumns)
          <> " FROM cargo c JOIN shipper s ON s.id = c.shipper_id \
             \ ORDER BY c.booking_id LIMIT 100"
      ) ::
      IO [CargoRow]
  mapM rowToCargo rows

findCargo :: Connection -> Text -> IO (Maybe Cargo)
findCargo conn bid = do
  rows <-
    query
      conn
      ( "SELECT "
          <> Query (T.encodeUtf8 cargoSelectColumns)
          <> " FROM cargo c JOIN shipper s ON s.id = c.shipper_id \
             \ WHERE c.booking_id = ? LIMIT 1"
      )
      (Only bid) ::
      IO [CargoRow]
  case rows of
    (r : _) -> Just <$> rowToCargo r
    [] -> pure Nothing

{- | DB 列値から CargoType を復元する際の失敗理由 (M-02 改善)。
詳細を呼び出し元に伝え、WARN ログで運用者が原因特定できるようにする。
-}
data ParseCargoTypeIssue
  = HazardousFieldsMissing -- "HAZARDOUS" だが必須カラムが NULL
  | RefrigeratedFieldsMissing -- "REFRIGERATED" だが必須カラムが NULL
  | UnknownCargoTypeText !Text -- "GENERAL" / "HAZARDOUS" / "REFRIGERATED" 以外
  deriving stock (Eq, Show)

{- | DB 列値から CargoType を復元する。CHECK 制約で整合性が保たれている前提
(スマートコンストラクタを通さず直接構築)。不整合時は Left でその理由を返し、
呼び出し元 (rowToCargo) が WARN ログを出して General にフォールバックする。
-}
parseCargoType ::
  Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Double ->
  Maybe Double ->
  Maybe Text ->
  Either ParseCargoTypeIssue CargoType
parseCargoType "GENERAL" _ _ _ _ _ _ = Right General
parseCargoType "HAZARDOUS" (Just hc) (Just un) (Just psn) _ _ _ =
  Right $
    Hazardous
      HazardousDeclaration
        { hazardousClass = hc
        , unNumber = un
        , properShippingName = psn
        }
parseCargoType "HAZARDOUS" _ _ _ _ _ _ = Left HazardousFieldsMissing
parseCargoType "REFRIGERATED" _ _ _ (Just lo) (Just hi) (Just u) =
  Right $
    Refrigerated
      TemperatureRequirement
        { minTemperature = lo
        , maxTemperature = hi
        , temperatureUnit = temperatureUnitFromText u
        }
parseCargoType "REFRIGERATED" _ _ _ _ _ _ = Left RefrigeratedFieldsMissing
parseCargoType other _ _ _ _ _ _ = Left (UnknownCargoTypeText other)

{- | L-03 (IT3): TemperatureUnit ↔ DB 文字列 ("C" / "F") の対称ヘルパ。
parseCargoType (read) と cargoTypeColumns (write) で同じ写像を使う。
-}
temperatureUnitToText :: TemperatureUnit -> Text
temperatureUnitToText Celsius = "C"
temperatureUnitToText Fahrenheit = "F"

-- | "F" 以外はすべて Celsius にフォールバック (DB CHECK 制約で 'C'/'F' のみ前提)。
temperatureUnitFromText :: Text -> TemperatureUnit
temperatureUnitFromText "F" = Fahrenheit
temperatureUnitFromText _ = Celsius

textToBookingStatus :: Text -> BookingStatus
textToBookingStatus "Submitted" = Submitted
textToBookingStatus "RouteProposed" = RouteProposed
textToBookingStatus "RouteAssigned" = RouteAssigned
textToBookingStatus "Confirmed" = Confirmed
textToBookingStatus "Settled" = Settled
textToBookingStatus "Cancelled" = Cancelled
textToBookingStatus "Closed" = Closed
textToBookingStatus _ = Draft

-- T-01 (IT2): shipper サロゲートキー解決失敗を `error` で潰さず
-- `Left (ShipperNotFound ...)` を返して呼び出し元に伝播する。
saveCargo :: Connection -> Cargo -> IO (Either DomainError ())
saveCargo conn c = do
  let ShipperRef sidBusiness = cargoShipperRef c
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
    , Just (temperatureUnitToText (temperatureUnit r))
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
bookingStatusToText RouteAssigned = "RouteAssigned"
bookingStatusToText Confirmed = "Confirmed"
bookingStatusToText Settled = "Settled"
bookingStatusToText Cancelled = "Cancelled"
bookingStatusToText Closed = "Closed"
