{-# LANGUAGE OverloadedStrings #-}

{- | PostgresShipperRepository の統合テスト (IT1)

DATABASE_URL があれば実行、なければ pendingWith でスキップ。
-}
module Shipper.Infrastructure.PostgresShipperRepositorySpec (spec) where

import qualified Data.ByteString.Char8 as BC
import Database.PostgreSQL.Simple
  ( Only (..),
    close,
    connectPostgreSQL,
    execute,
  )
import System.Environment (lookupEnv)
import Test.Hspec

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank (..),
    CorporateNumber (..),
    Shipper (..),
    ShipperKind (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (Address (..))
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Infrastructure.PostgresShipperRepository
  ( newPostgresShipperRepository,
  )

testEmail1, testEmail2 :: ContactEmail
testEmail1 = ContactEmail "integ-shipper1@example.com"
testEmail2 = ContactEmail "integ-shipper2@example.com"

cleanup :: connection -> IO ()
cleanup _ = pure ()

dummyIndividual :: Shipper
dummyIndividual =
  Shipper
    { shipperId = ShipperId "SHP-Z1Y2X3"
    , shipperEmail = testEmail1
    , shipperAddress = Address "1-1-1 Test, Tokyo"
    , shipperKind = Individual
    }

dummyCorporate :: Shipper
dummyCorporate =
  Shipper
    { shipperId = ShipperId "SHP-A9B8C7"
    , shipperEmail = testEmail2
    , shipperAddress = Address "2-2-2 Test, Osaka"
    , shipperKind = Corporate (CorporateNumber "9876543210123") Gold
    }

spec :: Spec
spec = describe "PostgresShipperRepository [INTEGRATION]" $ do
  it "Individual 荷主を保存して検索できる" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing -> pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        _ <- execute conn "DELETE FROM shipper WHERE email = ?" (Only ("integ-shipper1@example.com" :: String))
        let repo = newPostgresShipperRepository conn
        save repo dummyIndividual
        result <- findByContactEmail repo testEmail1
        case result of
          Just s -> do
            shipperId s `shouldBe` ShipperId "SHP-Z1Y2X3"
            shipperKind s `shouldBe` Individual
          Nothing -> expectationFailure "expected Just Individual shipper"
        _ <- execute conn "DELETE FROM shipper WHERE email = ?" (Only ("integ-shipper1@example.com" :: String))
        close conn

  it "Corporate 荷主を保存して検索できる (法人番号と契約ランク含む)" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing -> pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        _ <- execute conn "DELETE FROM shipper WHERE email = ?" (Only ("integ-shipper2@example.com" :: String))
        let repo = newPostgresShipperRepository conn
        save repo dummyCorporate
        result <- findByContactEmail repo testEmail2
        case result of
          Just s -> case shipperKind s of
            Corporate (CorporateNumber cn) Gold -> cn `shouldBe` "9876543210123"
            other -> expectationFailure ("expected Corporate Gold but got " <> show other)
          Nothing -> expectationFailure "expected Just Corporate shipper"
        _ <- execute conn "DELETE FROM shipper WHERE email = ?" (Only ("integ-shipper2@example.com" :: String))
        close conn

  it "存在しないメールは Nothing" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing -> pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        let repo = newPostgresShipperRepository conn
        result <- findByContactEmail repo (ContactEmail "ghost@example.com")
        result `shouldBe` Nothing
        close conn
