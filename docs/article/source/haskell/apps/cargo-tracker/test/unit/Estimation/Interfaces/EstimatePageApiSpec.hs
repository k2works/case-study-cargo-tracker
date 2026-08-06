{-# LANGUAGE OverloadedStrings #-}

-- | EstimatePageApi のテスト (U-01 / US01, IT3)
module Estimation.Interfaces.EstimatePageApiSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Network.Wai (Application)
import Network.Wai.Test (simpleHeaders)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Estimate (Estimate)
import Cargotracker.Estimation.Interfaces.EstimatePageApi (estimatePageApp)

makeRepo :: IO (EstimateRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Estimate])
  pure
    EstimateRepository
      { saveEstimate = \e -> do
          modifyIORef' ref (e :)
          pure (Right ())
      , findEstimateById = \_ -> pure Nothing
      , findAllEstimates = readIORef ref
      }

mkApp :: IO Application
mkApp = fmap estimatePageApp makeRepo

spec :: Spec
spec = with mkApp $ do
  describe "GET /estimates/new (U-01)" $ do
    it "フォームを 200 で返す" $
      get "/estimates/new" `shouldRespondWith` 200

    it "?error=deadline-format で 200 + エラーメッセージ" $
      get "/estimates/new?error=deadline-format" `shouldRespondWith` 200

  describe "POST /estimates (U-01)" $ do
    it "正常系は 303 + Location が /estimates/<uuid> を指す" $ do
      res <-
        request
          "POST"
          "/estimates"
          [("Content-Type", "application/x-www-form-urlencoded")]
          ( "shipperId=SHP-X1Y2Z3"
              <> "&origin=JPTYO"
              <> "&destination=USNYC"
              <> "&deadline=2026-12-31T00%3A00"
              <> "&cargoType=General"
              <> "&weight=1000.5"
          )
      shouldRespondWith (pure res) 303

    it "期限フォーマット不正は 303 + /estimates/new?error=deadline-format" $
      request
        "POST"
        "/estimates"
        [("Content-Type", "application/x-www-form-urlencoded")]
        ( "shipperId=SHP-X1Y2Z3"
            <> "&origin=JPTYO"
            <> "&destination=USNYC"
            <> "&deadline=BAD"
            <> "&cargoType=General"
            <> "&weight=1000"
        )
        `shouldRespondWith` 303
          { matchHeaders = ["Location" <:> "/estimates/new?error=deadline-format"]
          }

    it "重量フォーマット不正は 303 + /estimates/new?error=weight-format" $
      request
        "POST"
        "/estimates"
        [("Content-Type", "application/x-www-form-urlencoded")]
        ( "shipperId=SHP-X1Y2Z3"
            <> "&origin=JPTYO"
            <> "&destination=USNYC"
            <> "&deadline=2026-12-31T00%3A00"
            <> "&cargoType=General"
            <> "&weight=NaN"
        )
        `shouldRespondWith` 303
          { matchHeaders = ["Location" <:> "/estimates/new?error=weight-format"]
          }
  where
    _ = simpleHeaders -- avoid unused-import warning if removed
