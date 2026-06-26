{-# LANGUAGE OverloadedStrings #-}

-- | PostgresVoyageRepository の統合テスト (IT1)
module Routing.Infrastructure.PostgresVoyageRepositorySpec (spec) where

import qualified Data.ByteString.Char8 as BC
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    close,
    connectPostgreSQL,
    execute,
  )
import System.Environment (lookupEnv)
import Test.Hspec

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber (..))
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..), mkVoyage)
import Cargotracker.Routing.Infrastructure.PostgresVoyageRepository
  ( newPostgresVoyageRepository,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))

ts :: Integer -> UTCTime
ts hour =
  addUTCTime (fromIntegral (hour * 3600)) baseTime
  where
    baseTime =
      UTCTime
        (fromGregorian 2027 6 1)
        (secondsToDiffTime 0)

cleanupVoyage :: Connection -> IO ()
cleanupVoyage conn = do
  _ <- execute conn "DELETE FROM voyage WHERE voyage_number = ?" (Only ("V-INT01" :: String))
  pure ()

spec :: Spec
spec = describe "PostgresVoyageRepository [INTEGRATION]" $ do
  it "2 区間の航海を保存して検索できる" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing -> pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        cleanupVoyage conn
        let vn = VoyageNumber "V-INT01"
            m1 =
              CarrierMovement
                { departureLocation = UnLocode "JPTYO"
                , arrivalLocation = UnLocode "HKHKG"
                , departureTime = ts 1
                , arrivalTime = ts 12
                }
            m2 =
              CarrierMovement
                { departureLocation = UnLocode "HKHKG"
                , arrivalLocation = UnLocode "USNYC"
                , departureTime = ts 13
                , arrivalTime = ts 36
                }
        Right voyage <- pure (mkVoyage vn [m1, m2])
        let repo = newPostgresVoyageRepository conn
        saveVoyage repo voyage
        result <- findByVoyageNumber repo vn
        case result of
          Just loaded -> do
            voyageNumber loaded `shouldBe` vn
            length (carrierMovements loaded) `shouldBe` 2
            map departureLocation (carrierMovements loaded)
              `shouldBe` [UnLocode "JPTYO", UnLocode "HKHKG"]
          Nothing -> expectationFailure "expected Just voyage"
        cleanupVoyage conn
        close conn

  it "存在しない航海番号は Nothing" $ do
    mUrl <- lookupEnv "DATABASE_URL"
    case mUrl of
      Nothing -> pendingWith "DATABASE_URL not set; skipped"
      Just url -> do
        conn <- connectPostgreSQL (BC.pack url)
        let repo = newPostgresVoyageRepository conn
        result <- findByVoyageNumber repo (VoyageNumber "GHOST")
        result `shouldBe` Nothing
        close conn
