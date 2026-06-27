{-# LANGUAGE OverloadedStrings #-}

{- | 航海スケジュール API のテスト (IT1 US24 4.5)

POST /voyages で航海番号と区間リストを受け取り、Voyage 集約を生成・保存する。

- 201 Created: 正常登録
- 422: バリデーション失敗 (VoyageNumber 不正 / 区間連続性違反)
- 409 Conflict: 同一航海番号の重複
- 400: JSON パース失敗
-}
module Routing.Interfaces.VoyageApiSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.IORef (modifyIORef', newIORef, readIORef)
import Network.HTTP.Types (methodPost)
import Network.Wai (Application)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Routing.Interfaces.VoyageApi (voyageApp)

makeRepo :: IO (VoyageRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Voyage])
  pure
    VoyageRepository
      { findByVoyageNumber = \vn -> do
          xs <- readIORef ref
          pure
            ( case [v | v <- xs, voyageNumber v == vn] of
                (x : _) -> Just x
                [] -> Nothing
            )
      , saveVoyage = \v -> modifyIORef' ref (v :)
      , updateVoyage = \_ -> pure (Right ())
      }

testApp :: IO Application
testApp = voyageApp <$> makeRepo

validBody :: LBC.ByteString
validBody =
  "{\"voyageNumber\":\"V0001\",\"movements\":[ "
    <> "{\"departure\":\"JPTYO\",\"arrival\":\"USNYC\","
    <> "\"departureTime\":\"2026-12-01T01:00:00Z\","
    <> "\"arrivalTime\":\"2026-12-02T00:00:00Z\"}]}"

emptyNumberBody :: LBC.ByteString
emptyNumberBody =
  "{\"voyageNumber\":\"\",\"movements\":[ "
    <> "{\"departure\":\"JPTYO\",\"arrival\":\"USNYC\","
    <> "\"departureTime\":\"2026-12-01T01:00:00Z\","
    <> "\"arrivalTime\":\"2026-12-02T00:00:00Z\"}]}"

discontinuousBody :: LBC.ByteString
discontinuousBody =
  "{\"voyageNumber\":\"V0002\",\"movements\":[ "
    <> "{\"departure\":\"JPTYO\",\"arrival\":\"HKHKG\","
    <> "\"departureTime\":\"2026-12-01T01:00:00Z\","
    <> "\"arrivalTime\":\"2026-12-01T12:00:00Z\"},"
    <> "{\"departure\":\"USNYC\",\"arrival\":\"JPTYO\","
    <> "\"departureTime\":\"2026-12-01T13:00:00Z\","
    <> "\"arrivalTime\":\"2026-12-02T01:00:00Z\"}]}"

spec :: Spec
spec = with testApp $ do
  describe "POST /voyages" $ do
    it "正常な入力は 201" $
      request methodPost "/voyages" [("Content-Type", "application/json")] validBody
        `shouldRespondWith` 201

    it "VoyageNumber 空は 422" $
      request methodPost "/voyages" [("Content-Type", "application/json")] emptyNumberBody
        `shouldRespondWith` 422

    it "区間連続性違反は 422" $
      request methodPost "/voyages" [("Content-Type", "application/json")] discontinuousBody
        `shouldRespondWith` 422

    it "壊れた JSON は 400" $
      request methodPost "/voyages" [("Content-Type", "application/json")] "not-json"
        `shouldRespondWith` 400

    it "同じ航海番号の重複は 409" $ do
      _ <- request methodPost "/voyages" [("Content-Type", "application/json")] validBody
      request methodPost "/voyages" [("Content-Type", "application/json")] validBody
        `shouldRespondWith` 409
