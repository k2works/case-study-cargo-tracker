{-# LANGUAGE OverloadedStrings #-}

{- | ShipperSearchApi の htmx 部分 HTML テスト (T-04, IT2)

GET /shippers/search?q=... が、検索結果を Bootstrap list-group 部分 HTML として
返すことを検証する。IT1 では実装のみで保証なし (リグレッション検知不能) だった。
-}
module Shipper.Interfaces.ShipperSearchApiSpec (spec) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchBody (..))

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper
  ( Shipper,
    mkIndividualShipper,
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (Address (..))
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Interfaces.ShipperSearchApi (shipperSearchApp)

bodyContains :: BS.ByteString -> MatchBody
bodyContains needle = MatchBody $ \_ body ->
  if needle `isInfixOfBS` LBS.toStrict body
    then Nothing
    else Just ("body does not contain: " <> show needle)

isInfixOfBS :: BS.ByteString -> BS.ByteString -> Bool
isInfixOfBS needle hay =
  BS.length needle == 0 || any (BS.isPrefixOf needle) (BS.tails hay)

seedShipper :: Shipper
seedShipper =
  mkIndividualShipper
    (ShipperId "SHP-ALICE1")
    (ContactEmail "alice@example.com")
    (Address "Tokyo")

repoFor :: [Shipper] -> ShipperRepository IO
repoFor results =
  ShipperRepository
    { findByContactEmail = \_ -> pure Nothing
    , findById = \_ -> pure Nothing
    , save = \_ -> pure ()
    , searchByQuery = \_ -> pure results
    }

spec :: Spec
spec = do
  describe "GET /shippers/search (T-04 htmx 部分 HTML)" $ do
    with (pure (shipperSearchApp (repoFor [seedShipper]))) $ do
      it "ヒットありなら list-group-item に shipperId と email を含む" $
        get "/shippers/search?q=alice"
          `shouldRespondWith` 200
            { matchBody = bodyContains "SHP-ALICE1 \xe2\x80\x94 alice@example.com"
            }

      it "結果は完全な HTML ドキュメントではなく部分 HTML を返す (<html> タグなし)" $ do
        res <- get "/shippers/search?q=alice"
        let body = LBS.toStrict (simpleBody res)
        liftIO $ shouldNotSatisfy body (isInfixOfBS "<html")
        liftIO $ shouldNotSatisfy body (isInfixOfBS "<!DOCTYPE")

    with (pure (shipperSearchApp (repoFor []))) $
      it "ヒットなしなら「該当なし」プレースホルダを返す" $
        get "/shippers/search?q=ghost"
          `shouldRespondWith` 200
            { matchBody = bodyContains "\xe8\xa9\xb2\xe5\xbd\x93\xe3\x81\xaa\xe3\x81\x97"
            }
