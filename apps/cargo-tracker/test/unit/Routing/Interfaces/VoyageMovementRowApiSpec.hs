{-# LANGUAGE OverloadedStrings #-}

{- | VoyageMovementRowApi の htmx 部分 HTML テスト (T-04, IT2)

GET /voyages/new/movement-row?index=N が、Voyage フォームに動的追加される
区間入力行の部分 HTML を返すことを検証する。
-}
module Routing.Interfaces.VoyageMovementRowApiSpec (spec) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchBody (..))

import Cargotracker.Routing.Interfaces.VoyageMovementRowApi
  ( voyageMovementRowApp,
  )

bodyContains :: BS.ByteString -> MatchBody
bodyContains needle = MatchBody $ \_ body ->
  if needle `isInfixOfBS` LBS.toStrict body
    then Nothing
    else Just ("body does not contain: " <> show needle)

isInfixOfBS :: BS.ByteString -> BS.ByteString -> Bool
isInfixOfBS needle hay =
  BS.length needle == 0 || any (BS.isPrefixOf needle) (BS.tails hay)

spec :: Spec
spec = with (pure voyageMovementRowApp) $ do
  describe "GET /voyages/new/movement-row (T-04 htmx 部分 HTML)" $ do
    it "index 指定なしは「区間 4」を返す (デフォルト)" $
      get "/voyages/new/movement-row"
        `shouldRespondWith` 200
          { matchBody = bodyContains "\xe5\x8c\xba\xe9\x96\x93 4"
          }

    it "index=5 を指定すると「区間 5」を返す" $
      get "/voyages/new/movement-row?index=5"
        `shouldRespondWith` 200
          { matchBody = bodyContains "\xe5\x8c\xba\xe9\x96\x93 5"
          }

    it "返却は部分 HTML (<html>/<!DOCTYPE> を含まない)" $ do
      res <- get "/voyages/new/movement-row?index=2"
      let body = LBS.toStrict (simpleBody res)
      liftIO $ shouldNotSatisfy body (isInfixOfBS "<html")
      liftIO $ shouldNotSatisfy body (isInfixOfBS "<!DOCTYPE")

    it "movementN* 入力フィールドの命名規約に従う (name 属性に movement2* を含む)" $
      get "/voyages/new/movement-row?index=2"
        `shouldRespondWith` 200
          { matchBody = bodyContains "name=\"movement2Departure\""
          }
