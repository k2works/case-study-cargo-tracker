{- | 温度管理条件 値オブジェクト (US05, IT2)

冷凍・冷蔵貨物 (Cargo.cargoType == Refrigerated) に必須の追加情報。
- minTemperature: 許容下限温度
- maxTemperature: 許容上限温度 (min <= max)
- temperatureUnit: 'C' (摂氏) or 'F' (華氏)
-}
module Cargotracker.Booking.Domain.Model.Value.TemperatureRequirement
  ( TemperatureRequirement (..),
    TemperatureUnit (..),
    mkTemperatureRequirement,
    parseTemperatureUnit,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data TemperatureUnit
  = Celsius
  | Fahrenheit
  deriving stock (Eq, Show)

data TemperatureRequirement = TemperatureRequirement
  { minTemperature :: !Double
  , maxTemperature :: !Double
  , temperatureUnit :: !TemperatureUnit
  }
  deriving stock (Eq, Show)

parseTemperatureUnit :: Text -> Either DomainError TemperatureUnit
parseTemperatureUnit "C" = Right Celsius
parseTemperatureUnit "F" = Right Fahrenheit
parseTemperatureUnit other =
  Left (InvalidBookingId ("invalid temperature unit: " <> other))

mkTemperatureRequirement ::
  Double ->
  Double ->
  TemperatureUnit ->
  Either DomainError TemperatureRequirement
mkTemperatureRequirement minT maxT unit
  | minT > maxT =
      Left (InvalidBookingId "min temperature must be <= max temperature")
  | otherwise =
      Right
        TemperatureRequirement
          { minTemperature = minT
          , maxTemperature = maxT
          , temperatureUnit = unit
          }
