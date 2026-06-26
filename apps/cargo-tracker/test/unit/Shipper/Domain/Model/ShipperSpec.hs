{- | Shipper 集約のテスト (IT1 US02/US03 2.1)

US02 個人荷主 / US03 法人荷主は同一集約ルート Shipper の sum type で表現する。
法人は CorporateNumber (13 桁) と ContractRank (Bronze/Silver/Gold) を持つ。
-}
module Shipper.Domain.Model.ShipperSpec (spec) where

import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank (..),
    CorporateNumber (..),
    Shipper (..),
    ShipperKind (..),
    mkCorporateNumber,
    mkCorporateShipper,
    mkIndividualShipper,
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (Address (..), mkAddress)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail (..), mkContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..), mkShipperId)

validId :: ShipperId
validId =
  case mkShipperId "SHP-A1B2C3" of
    Right s -> s
    Left _ -> error "test setup: invalid id"

validEmail :: ContactEmail
validEmail =
  case mkContactEmail "shipper@example.com" of
    Right e -> e
    Left _ -> error "test setup: invalid email"

validAddress :: Address
validAddress =
  case mkAddress "東京都港区芝公園 4-2-8" of
    Right a -> a
    Left _ -> error "test setup: invalid address"

spec :: Spec
spec = do
  describe "ShipperId" $ do
    it "SHP- プレフィックスがないと不正" $
      mkShipperId "A1B2C3" `shouldBe` Left (InvalidShipperId "expected SHP-XXXXXX")
    it "プレフィックス後が 6 文字でないと不正" $
      mkShipperId "SHP-ABC" `shouldBe` Left (InvalidShipperId "expected SHP-XXXXXX")
    it "正しいフォーマットは構築できる" $
      mkShipperId "SHP-A1B2C3" `shouldBe` Right (ShipperId "SHP-A1B2C3")

  describe "Address" $ do
    it "空文字は不正" $
      mkAddress "" `shouldBe` Left (InvalidShipperId "empty address")
    it "500 文字超は不正" $
      let long = T.replicate 501 "あ"
       in mkAddress long `shouldBe` Left (InvalidShipperId "address too long (max 500)")
    it "500 文字は構築できる" $
      let just500 = T.replicate 500 "あ"
       in mkAddress just500 `shouldBe` Right (Address just500)

  describe "ContactEmail" $ do
    it "@ なしは不正" $
      mkContactEmail "noatsign" `shouldBe` Left (InvalidShipperId "invalid email")
    it "有効なメールは構築できる" $
      mkContactEmail "x@y.z" `shouldBe` Right (ContactEmail "x@y.z")

  describe "CorporateNumber" $ do
    it "13 桁ではないと不正" $
      mkCorporateNumber "12345" `shouldBe` Left (InvalidShipperId "expected 13 digits")
    it "数字以外を含むと不正" $
      mkCorporateNumber "12345678901AB" `shouldBe` Left (InvalidShipperId "digits only")
    it "13 桁の数字は構築できる" $
      mkCorporateNumber "1234567890123" `shouldBe` Right (CorporateNumber "1234567890123")

  describe "ContractRank" $ do
    it "Bronze/Silver/Gold の 3 段階" $
      [Bronze, Silver, Gold] `shouldBe` [minBound .. maxBound]

  describe "mkIndividualShipper (US02)" $ do
    it "ID/メール/住所だけで構築でき kind は Individual" $ do
      let s = mkIndividualShipper validId validEmail validAddress
      shipperId s `shouldBe` validId
      shipperKind s `shouldBe` Individual

  describe "mkCorporateShipper (US03)" $ do
    it "ID/メール/住所/法人番号/契約ランクで構築でき kind は Corporate" $ do
      Right cn <- pure (mkCorporateNumber "1234567890123")
      let s =
            mkCorporateShipper
              validId
              validEmail
              validAddress
              cn
              Gold
      shipperKind s `shouldBe` Corporate cn Gold
