{-# LANGUAGE OverloadedStrings #-}

{- | 荷主登録 API のテスト (IT1 US02/03 2.5)

POST /shippers で {id, email, address, kind} を JSON で受け取り、
RegisterShipperCommand 経由で集約を生成・保存する。

- 201 Created: 正常登録
- 422 Unprocessable Entity: バリデーション失敗
- 409 Conflict: メール重複 (ConcurrentModification)
- 400 Bad Request: JSON パース失敗
-}
module Shipper.Interfaces.ShipperApiSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.IORef (modifyIORef', newIORef, readIORef)
import Network.HTTP.Types (methodPost)
import Network.Wai (Application)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper (Shipper (..))
import Cargotracker.Shipper.Interfaces.ShipperApi (shipperApp)

-- フェイク Repository (in-memory)
makeRepo :: IO (ShipperRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Shipper])
  pure
    ShipperRepository
      { findByContactEmail = \e -> do
          xs <- readIORef ref
          pure
            ( case [s | s <- xs, shipperEmail s == e] of
                (x : _) -> Just x
                [] -> Nothing
            )
      , save = \s -> modifyIORef' ref (s :)
      }

testApp :: IO Application
testApp = shipperApp <$> makeRepo

individualBody :: LBC.ByteString
individualBody =
  "{\"shipperId\":\"SHP-A1B2C3\",\"email\":\"alice@example.com\","
    <> "\"address\":\"4-2-8 Shibakoen, Minato-ku, Tokyo\",\"kind\":\"individual\"}"

corporateBody :: LBC.ByteString
corporateBody =
  "{\"shipperId\":\"SHP-D4E5F6\",\"email\":\"corp@example.com\","
    <> "\"address\":\"1-1 Marunouchi, Chiyoda-ku, Tokyo\","
    <> "\"kind\":\"corporate\",\"corporateNumber\":\"1234567890123\","
    <> "\"contractRank\":\"Gold\"}"

invalidIdBody :: LBC.ByteString
invalidIdBody =
  "{\"shipperId\":\"WRONG\",\"email\":\"x@y.z\",\"address\":\"a\",\"kind\":\"individual\"}"

spec :: Spec
spec = with testApp $ do
  describe "POST /shippers" $ do
    it "個人荷主登録は 201" $
      request methodPost "/shippers" [("Content-Type", "application/json")] individualBody
        `shouldRespondWith` 201

    it "法人荷主登録は 201" $
      request methodPost "/shippers" [("Content-Type", "application/json")] corporateBody
        `shouldRespondWith` 201

    it "ID が不正なら 422" $
      request methodPost "/shippers" [("Content-Type", "application/json")] invalidIdBody
        `shouldRespondWith` 422

    it "メール重複は 409 Conflict" $ do
      -- 1 度目は成功
      _ <- request methodPost "/shippers" [("Content-Type", "application/json")] individualBody
      -- 同じメールで再登録
      let dup =
            "{\"shipperId\":\"SHP-X9Y8Z7\",\"email\":\"alice@example.com\","
              <> "\"address\":\"Different Address 9-9-9\",\"kind\":\"individual\"}"
      request methodPost "/shippers" [("Content-Type", "application/json")] dup
        `shouldRespondWith` 409

    it "壊れた JSON は 400" $
      request methodPost "/shippers" [("Content-Type", "application/json")] "not-json"
        `shouldRespondWith` 400
