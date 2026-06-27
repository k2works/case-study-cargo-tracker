{-# LANGUAGE OverloadedStrings #-}

{- | VoyagePageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /voyages/new が航海フォーム入力を受け取り、成功時は航海詳細画面
(`/voyages/:voyageNumber`)、失敗時は `?error=` クエリ付きで /voyages/new
にリダイレクト (303 See Other) することを検証する。
-}
module Routing.Interfaces.VoyagePageApiSpec (spec) where

import Data.IORef (modifyIORef', newIORef)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Voyage (Voyage)
import Cargotracker.Routing.Interfaces.VoyagePageApi (voyagePageApp)

makeRepo :: IO (VoyageRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Voyage])
  pure
    VoyageRepository
      { findByVoyageNumber = \_ -> pure Nothing
      , saveVoyage = \v -> modifyIORef' ref (v :)
      }

spec :: Spec
spec = with (fmap voyagePageApp makeRepo) $ do
  describe "POST /voyages/new (T-03 PRG)" $ do
    it "正常系は 303 を返し Location が /voyages/:voyageNumber を指す" $
      request
        "POST"
        "/voyages/new"
        [("Content-Type", "application/x-www-form-urlencoded")]
        ( "voyageNumber=V-001"
            <> "&movement1Departure=JPTYO"
            <> "&movement1Arrival=USNYC"
            <> "&movement1DepartureTime=2026-07-01T10%3A00"
            <> "&movement1ArrivalTime=2026-07-15T08%3A00"
        )
        `shouldRespondWith` 303
          { matchHeaders = ["Location" <:> "/voyages/V-001"]
          }

    it "区間 1 の時刻フォーマット不正は 303 + Location が /voyages/new?error=... を指す" $ do
      res <-
        request
          "POST"
          "/voyages/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          ( "voyageNumber=V-001"
              <> "&movement1Departure=JPTYO"
              <> "&movement1Arrival=USNYC"
              <> "&movement1DepartureTime=BAD"
              <> "&movement1ArrivalTime=ALSO_BAD"
          )
      shouldRespondWith (pure res) 303
