{- | Voyage 集約のテスト (IT1 US24 4.1)

VoyageNumber + 1..* CarrierMovement で構成される。
CarrierMovement の連続性 (前区間 arrival == 次区間 departure) を
集約構築時に検証する (LegContinuityViolation エラー)。
-}
module Routing.Domain.Model.VoyageSpec (spec) where

import qualified Data.Text as T
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber (..),
    mkVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage
  ( Voyage (..),
    mkVoyage,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

ts :: Integer -> UTCTime
ts hour =
  UTCTime
    (fromGregorian 2026 12 1)
    (secondsToDiffTime (hour * 3600))

spec :: Spec
spec = do
  describe "VoyageNumber" $ do
    it "空文字は不正" $
      mkVoyageNumber "" `shouldBe` Left (InvalidVoyageNumber "empty")
    it "20 文字超は不正" $
      mkVoyageNumber (T.replicate 21 "V") `shouldBe` Left (InvalidVoyageNumber "too long (max 20)")
    it "20 文字までは構築できる" $
      mkVoyageNumber "V0001" `shouldBe` Right (VoyageNumber "V0001")

  describe "mkVoyage" $ do
    it "1 区間のみの航海は構築できる" $ do
      Right vn <- pure (mkVoyageNumber "V0001")
      Right tyo <- pure (mkUnLocode "JPTYO")
      Right nyc <- pure (mkUnLocode "USNYC")
      let mv =
            CarrierMovement
              { departureLocation = tyo
              , arrivalLocation = nyc
              , departureTime = ts 1
              , arrivalTime = ts 24
              }
      case mkVoyage vn [mv] of
        Right v -> do
          voyageNumber v `shouldBe` vn
          length (carrierMovements v) `shouldBe` 1
        Left e -> expectationFailure ("expected Right but got " <> show e)

    it "区間 0 件は不正" $ do
      Right vn <- pure (mkVoyageNumber "V0001")
      mkVoyage vn [] `shouldBe` Left (LegContinuityViolation "at least 1 movement required")

    it "区間連続性が崩れていると LegContinuityViolation" $ do
      Right vn <- pure (mkVoyageNumber "V0001")
      Right tyo <- pure (mkUnLocode "JPTYO")
      Right nyc <- pure (mkUnLocode "USNYC")
      Right hkg <- pure (mkUnLocode "HKHKG")
      let m1 = CarrierMovement tyo hkg (ts 1) (ts 12)
          -- m1 は HKHKG 到着なのに m2 は USNYC からの出発 (連続性違反)
          m2 = CarrierMovement nyc tyo (ts 13) (ts 25)
      case mkVoyage vn [m1, m2] of
        Left (LegContinuityViolation _) -> pure ()
        other -> expectationFailure ("expected LegContinuityViolation but got " <> show other)

    it "連続した 2 区間は構築できる" $ do
      Right vn <- pure (mkVoyageNumber "V0001")
      Right tyo <- pure (mkUnLocode "JPTYO")
      Right hkg <- pure (mkUnLocode "HKHKG")
      Right nyc <- pure (mkUnLocode "USNYC")
      let m1 = CarrierMovement tyo hkg (ts 1) (ts 12)
          m2 = CarrierMovement hkg nyc (ts 13) (ts 25)
      case mkVoyage vn [m1, m2] of
        Right v -> length (carrierMovements v) `shouldBe` 2
        Left e -> expectationFailure ("expected Right but got " <> show e)
