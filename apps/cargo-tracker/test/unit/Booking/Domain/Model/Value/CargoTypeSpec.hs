{- | CargoType / HazardousDeclaration / TemperatureRequirement VO の
テスト (US04+US05, IT2)
-}
module Booking.Domain.Model.Value.CargoTypeSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Value.CargoType
  ( CargoType (..),
    cargoTypeToText,
  )
import Cargotracker.Booking.Domain.Model.Value.HazardousDeclaration
  ( HazardousDeclaration (..),
    mkHazardousDeclaration,
  )
import Cargotracker.Booking.Domain.Model.Value.TemperatureRequirement
  ( TemperatureRequirement (..),
    TemperatureUnit (..),
    mkTemperatureRequirement,
    parseTemperatureUnit,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

spec :: Spec
spec = do
  describe "CargoType.cargoTypeToText (DB CHECK 整合)" $ do
    it "General は GENERAL を返す" $
      cargoTypeToText General `shouldBe` "GENERAL"
    it "Hazardous は HAZARDOUS を返す" $ do
      Right d <- pure (mkHazardousDeclaration "3" "1203" "Gasoline")
      cargoTypeToText (Hazardous d) `shouldBe` "HAZARDOUS"
    it "Refrigerated は REFRIGERATED を返す" $ do
      Right r <- pure (mkTemperatureRequirement (-20) (-10) Celsius)
      cargoTypeToText (Refrigerated r) `shouldBe` "REFRIGERATED"

  describe "HazardousDeclaration" $ do
    it "正しい入力は Right" $ do
      Right d <- pure (mkHazardousDeclaration "3" "1203" "Gasoline")
      hazardousClass d `shouldBe` "3"
      unNumber d `shouldBe` "1203"
      properShippingName d `shouldBe` "Gasoline"
    it "前後空白は trim される" $ do
      Right d <- pure (mkHazardousDeclaration "  3 " " 1203 " "  Gasoline ")
      hazardousClass d `shouldBe` "3"
      unNumber d `shouldBe` "1203"
      properShippingName d `shouldBe` "Gasoline"
    it "クラスが空文字は Left" $
      mkHazardousDeclaration "" "1203" "Gasoline"
        `shouldBe` Left (InvalidBookingId "hazardous class must not be empty")
    it "UN 番号が 4 桁数字でないと Left" $
      mkHazardousDeclaration "3" "ABC1" "Gasoline"
        `shouldBe` Left (InvalidBookingId "UN number must be 4 digits (e.g. 1203)")
    it "品目名が空文字は Left" $
      mkHazardousDeclaration "3" "1203" "  "
        `shouldBe` Left (InvalidBookingId "proper shipping name must not be empty")
    it "品目名が 256 文字超は Left" $
      mkHazardousDeclaration "3" "1203" (T.replicate 256 "a")
        `shouldBe` Left (InvalidBookingId "proper shipping name too long (max 255)")

  describe "TemperatureRequirement" $ do
    it "min <= max は Right" $ do
      Right r <- pure (mkTemperatureRequirement (-20) (-10) Celsius)
      minTemperature r `shouldBe` -20
      maxTemperature r `shouldBe` -10
      temperatureUnit r `shouldBe` Celsius
    it "min == max も Right" $ do
      Right r <- pure (mkTemperatureRequirement 4 4 Celsius)
      maxTemperature r `shouldBe` 4
    it "min > max は Left" $
      mkTemperatureRequirement 10 0 Celsius
        `shouldBe` Left (InvalidBookingId "min temperature must be <= max temperature")

  describe "parseTemperatureUnit" $ do
    it "C は Celsius" $
      parseTemperatureUnit "C" `shouldBe` Right Celsius
    it "F は Fahrenheit" $
      parseTemperatureUnit "F" `shouldBe` Right Fahrenheit
    it "それ以外は Left" $
      parseTemperatureUnit "K"
        `shouldBe` Left (InvalidBookingId "invalid temperature unit: K")
