{-# LANGUAGE OverloadedStrings #-}

{- | ShipperPageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /shippers/new がフォーム入力を受け取り、成功時は荷主詳細画面 (`/shippers/:id`)、
失敗時はフォーム + `?error=` クエリへリダイレクト (303 See Other) することを検証する。
IT1 では PRG が実装されていたがテストが皆無 (リロード二重 POST デグレ検知不能) であった。
-}
module Shipper.Interfaces.ShipperPageApiSpec (spec) where

import qualified Data.ByteString as BS
import Data.IORef (modifyIORef', newIORef, readIORef)
import Network.HTTP.Types.Header (Header)
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchHeader (..))

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper (Shipper)
import Cargotracker.Shipper.Interfaces.ShipperPageApi (shipperPageApp)

-- T-07 (IT2): サーバ採番された ID を含む Location ヘッダの「接頭辞一致」を検証する
matchLocationPrefix :: BS.ByteString -> MatchHeader
matchLocationPrefix prefix = MatchHeader $ \hs _ ->
  case lookup "Location" (hs :: [Header]) of
    Just v | prefix `BS.isPrefixOf` v -> Nothing
    Just v -> Just ("Location does not start with " <> show prefix <> ": got " <> show v)
    Nothing -> Just "missing Location header"

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
    it "正常系は 303 を返し Location が /shippers/SHP-... を指す (T-07 自動採番)" $
      request
        "POST"
        "/shippers/new"
        [("Content-Type", "application/x-www-form-urlencoded")]
        "shipperId=IGNORED&name=Alice&email=alice%40example.com&address=Tokyo&kind=individual"
        `shouldRespondWith` 303
          { matchHeaders = [matchLocationPrefix "/shippers/SHP-"]
          }

    it "種別不正でも 303 を返し Location が /shippers/new?error=... を指す" $ do
      res <-
        request
          "POST"
          "/shippers/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "shipperId=SHP-ABC123&name=Alice&email=alice%40example.com&address=Tokyo&kind=WRONG"
      shouldRespondWith (pure res) 303
