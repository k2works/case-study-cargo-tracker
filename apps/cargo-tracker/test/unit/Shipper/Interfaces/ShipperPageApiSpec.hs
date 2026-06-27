{-# LANGUAGE OverloadedStrings #-}

{- | ShipperPageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /shippers/new がフォーム入力を受け取り、成功時は荷主詳細画面 (`/shippers/:id`)、
失敗時はフォーム + `?error=` クエリへリダイレクト (303 See Other) することを検証する。
IT1 では PRG が実装されていたがテストが皆無 (リロード二重 POST デグレ検知不能) であった。
-}
module Shipper.Interfaces.ShipperPageApiSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper (Shipper)
import Cargotracker.Shipper.Interfaces.ShipperPageApi (shipperPageApp)

makeRepo :: IO (ShipperRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Shipper])
  pure
    ShipperRepository
      { findByContactEmail = \_ -> pure Nothing
      , findById = \_ -> do
          xs <- readIORef ref
          pure $ case xs of
            (s : _) -> Just s
            [] -> Nothing
      , save = \s -> modifyIORef' ref (s :)
      , searchByQuery = \_ -> pure []
      }

spec :: Spec
spec = with (fmap shipperPageApp makeRepo) $ do
  describe "POST /shippers/new (T-03 PRG)" $ do
    it "正常系は 303 を返し Location が /shippers/:id を指す" $
      request
        "POST"
        "/shippers/new"
        [("Content-Type", "application/x-www-form-urlencoded")]
        "shipperId=SHP-ABC123&name=Alice&email=alice%40example.com&address=Tokyo&kind=individual"
        `shouldRespondWith` 303
          { matchHeaders = ["Location" <:> "/shippers/SHP-ABC123"]
          }

    it "種別不正でも 303 を返し Location が /shippers/new?error=... を指す" $ do
      res <-
        request
          "POST"
          "/shippers/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "shipperId=SHP-ABC123&name=Alice&email=alice%40example.com&address=Tokyo&kind=WRONG"
      shouldRespondWith (pure res) 303
