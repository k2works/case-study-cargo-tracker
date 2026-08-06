{-# LANGUAGE OverloadedStrings #-}

{- | ShipperSearchApi の htmx 部分 HTML テスト (T-04, IT2)

GET /shippers/search?q=... が、検索結果を Bootstrap list-group 部分 HTML として
返すことを検証する。IT1 では実装のみで保証なし (リグレッション検知不能) だった。
-}
module Shipper.Interfaces.ShipperSearchApiSpec (spec) where

import qualified Data.ByteString.Lazy as LBS
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper
  ( Shipper,
    mkIndividualShipper,
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (Address (..))
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (ShipperName (..))
import Cargotracker.Shipper.Interfaces.ShipperSearchApi (shipperSearchApp)
import Support.HspecWaiJa (bodyContainsText, isNotHtmlPage)

seedShipper :: Shipper
seedShipper =
  mkIndividualShipper
    (ShipperId "SHP-ALICE1")
    (ShipperName "Alice")
    (ContactEmail "alice@example.com")
    (Address "Tokyo")

repoFor :: [Shipper] -> ShipperRepository IO
repoFor results =
  ShipperRepository
    { findByContactEmail = \_ -> pure Nothing
    , findById = \_ -> pure Nothing
    , save = \_ -> pure ()
    , searchByQuery = \_ -> pure results
    , findAllShippers = pure []
    }

spec :: Spec
spec = do
  describe "GET /shippers/search (T-04 htmx 部分 HTML)" $ do
    with (pure (shipperSearchApp (repoFor [seedShipper]))) $ do
      it "ヒットありなら list-group-item に shipperId と email を含む" $
        get "/shippers/search?q=alice"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "SHP-ALICE1 — alice@example.com"
            }

      it "結果は完全な HTML ドキュメントではなく部分 HTML を返す (<html> タグなし)" $ do
        res <- get "/shippers/search?q=alice"
        let body = LBS.toStrict (simpleBody res)
        liftIO $ body `shouldSatisfy` isNotHtmlPage

    with (pure (shipperSearchApp (repoFor []))) $
      it "ヒットなしなら「該当なし」プレースホルダを返す" $
        get "/shippers/search?q=ghost"
          `shouldRespondWith` 200
            { matchBody = bodyContainsText "該当なし"
            }
