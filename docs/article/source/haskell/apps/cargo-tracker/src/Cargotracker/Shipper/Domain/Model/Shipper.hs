{- | Shipper 集約ルート (IT1 US02/03)

US02 個人荷主 / US03 法人荷主は同一の集約ルートで扱い、
`ShipperKind` sum type で区別する。
-}
module Cargotracker.Shipper.Domain.Model.Shipper
  ( Shipper (..),
    ShipperKind (..),
    CorporateNumber (..),
    ContractRank (..),
    mkCorporateNumber,
    mkIndividualShipper,
    mkCorporateShipper,
    discountPercentage,
  ) where

import Data.Char (isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Value.Address (Address)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId)
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (ShipperName)

newtype CorporateNumber = CorporateNumber {unCorporateNumber :: Text}
  deriving stock (Eq, Show)

data ContractRank
  = Bronze
  | Silver
  | Gold
  deriving stock (Eq, Show, Enum, Bounded)

data ShipperKind
  = Individual
  | Corporate !CorporateNumber !ContractRank
  deriving stock (Eq, Show)

data Shipper = Shipper
  { shipperId :: !ShipperId
  , shipperName :: !ShipperName
  {- ^ T-09 (IT2): 個人荷主は氏名、法人荷主は社名を保持。
  IT1 では Domain 未実装で email を placeholder にしていた。
  -}
  , shipperEmail :: !ContactEmail
  , shipperAddress :: !Address
  , shipperKind :: !ShipperKind
  }
  deriving stock (Eq, Show)

mkCorporateNumber :: Text -> Either DomainError CorporateNumber
mkCorporateNumber t
  | T.length t /= 13 = Left (InvalidShipperId "expected 13 digits")
  | not (T.all isDigit t) = Left (InvalidShipperId "digits only")
  | otherwise = Right (CorporateNumber t)

mkIndividualShipper ::
  ShipperId -> ShipperName -> ContactEmail -> Address -> Shipper
mkIndividualShipper sid name email addr =
  Shipper
    { shipperId = sid
    , shipperName = name
    , shipperEmail = email
    , shipperAddress = addr
    , shipperKind = Individual
    }

mkCorporateShipper ::
  ShipperId ->
  ShipperName ->
  ContactEmail ->
  Address ->
  CorporateNumber ->
  ContractRank ->
  Shipper
mkCorporateShipper sid name email addr cn rank =
  Shipper
    { shipperId = sid
    , shipperName = name
    , shipperEmail = email
    , shipperAddress = addr
    , shipperKind = Corporate cn rank
    }

{- | 荷主の契約割引率を百分率 Integer で返す (US22, IT7)。

法人契約は ContractRank (Bronze=5% / Silver=10% / Gold=15%)、
個人契約 (Individual) は 0% で固定。Pricing BC の Discount VO
(0-100 の Integer 百分率) と直接互換で、Shared/CrossBc/
ShipperToPricingHelper 経由で CalculateShippingCostCommand に渡す。

Rule 4 準拠: 戻り値は Integer で Pricing BC 型に依存しない。
-}
discountPercentage :: Shipper -> Integer
discountPercentage s = case shipperKind s of
  Individual -> 0
  Corporate _ Bronze -> 5
  Corporate _ Silver -> 10
  Corporate _ Gold -> 15
