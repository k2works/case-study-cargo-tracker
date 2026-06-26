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
  ) where

import Data.Char (isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Value.Address (Address)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId)

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

mkIndividualShipper :: ShipperId -> ContactEmail -> Address -> Shipper
mkIndividualShipper sid email addr =
  Shipper
    { shipperId = sid
    , shipperEmail = email
    , shipperAddress = addr
    , shipperKind = Individual
    }

mkCorporateShipper ::
  ShipperId ->
  ContactEmail ->
  Address ->
  CorporateNumber ->
  ContractRank ->
  Shipper
mkCorporateShipper sid email addr cn rank =
  Shipper
    { shipperId = sid
    , shipperEmail = email
    , shipperAddress = addr
    , shipperKind = Corporate cn rank
    }
