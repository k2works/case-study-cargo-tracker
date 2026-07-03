{- | Cross-BC helper resolveDiscountPercentageByShipperId のテスト (US22, IT7)

Pricing BC の CalculateShippingCostCommand が Shipper.discountPercentage を
Text-DTO (ADR-0012 / ADR-0004 Rule 4 準拠) 経由で解決するための helper。

* Text (raw shipperId) → mkShipperId → findById → discountPercentage → Integer
* 不正 shipperId 形式は InvalidShipperId
* 存在しない shipperId は ShipperNotFound
-}
module Shipper.Application.DiscountResolutionSpec (spec) where

import Data.IORef (newIORef, readIORef)
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports
  ( ShipperRepository (..),
    resolveDiscountPercentageByShipperId,
  )
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank (..),
    Shipper (..),
    mkCorporateNumber,
    mkCorporateShipper,
    mkIndividualShipper,
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (mkAddress)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (mkContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (mkShipperId)
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (mkShipperName)

sampleShipper :: ContractRank -> Shipper
sampleShipper rank =
  let Right sid = mkShipperId "SHP-CORP01"
      Right nm = mkShipperName "テスト法人"
      Right em = mkContactEmail "corp@example.com"
      Right ad = mkAddress "東京都千代田区丸の内 1-1"
      Right cn = mkCorporateNumber "1234567890123"
   in mkCorporateShipper sid nm em ad cn rank

individualShipper :: Shipper
individualShipper =
  let Right sid = mkShipperId "SHP-IND001"
      Right nm = mkShipperName "個人 太郎"
      Right em = mkContactEmail "ind@example.com"
      Right ad = mkAddress "神奈川県横浜市"
   in mkIndividualShipper sid nm em ad

makeRepo :: [Shipper] -> IO (ShipperRepository IO)
makeRepo initial = do
  ref <- newIORef initial
  pure
    ShipperRepository
      { findByContactEmail = \e -> do
          xs <- readIORef ref
          pure (case [s | s <- xs, shipperEmail s == e] of (x : _) -> Just x; [] -> Nothing)
      , findById = \sid -> do
          xs <- readIORef ref
          pure (case [s | s <- xs, shipperId s == sid] of (x : _) -> Just x; [] -> Nothing)
      , save = \_ -> pure ()
      , searchByQuery = \_ -> pure []
      , findAllShippers = pure []
      }

spec :: Spec
spec = describe "resolveDiscountPercentageByShipperId (US22, IT7)" $ do
  it "Corporate Gold の shipperId で 15 を返す" $ do
    repo <- makeRepo [sampleShipper Gold]
    result <- resolveDiscountPercentageByShipperId repo "SHP-CORP01"
    result `shouldBe` Right 15

  it "Corporate Silver の shipperId で 10 を返す" $ do
    repo <- makeRepo [sampleShipper Silver]
    result <- resolveDiscountPercentageByShipperId repo "SHP-CORP01"
    result `shouldBe` Right 10

  it "Corporate Bronze の shipperId で 5 を返す" $ do
    repo <- makeRepo [sampleShipper Bronze]
    result <- resolveDiscountPercentageByShipperId repo "SHP-CORP01"
    result `shouldBe` Right 5

  it "Individual の shipperId で 0 を返す" $ do
    repo <- makeRepo [individualShipper]
    result <- resolveDiscountPercentageByShipperId repo "SHP-IND001"
    result `shouldBe` Right 0

  it "存在しない shipperId は ShipperNotFound" $ do
    repo <- makeRepo []
    result <- resolveDiscountPercentageByShipperId repo "SHP-NOEXST"
    result `shouldBe` Left (ShipperNotFound "SHP-NOEXST")

  it "不正な shipperId 形式は InvalidShipperId (mkShipperId 由来)" $ do
    repo <- makeRepo []
    result <- resolveDiscountPercentageByShipperId repo "invalid-id"
    case result of
      Left (InvalidShipperId _) -> pure ()
      other -> expectationFailure ("expected InvalidShipperId, got: " <> show other)
