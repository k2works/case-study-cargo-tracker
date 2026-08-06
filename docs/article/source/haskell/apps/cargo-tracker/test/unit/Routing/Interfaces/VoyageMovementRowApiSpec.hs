{-# LANGUAGE OverloadedStrings #-}

{- | VoyageMovementRowApi の htmx 部分 HTML テスト (T-04, IT2)

GET /voyages/new/movement-row?index=N が、Voyage フォームに動的追加される
区間入力行の部分 HTML を返すことを検証する。
-}
module Routing.Interfaces.VoyageMovementRowApiSpec (spec) where

import qualified Data.ByteString.Lazy as LBS
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Routing.Interfaces.VoyageMovementRowApi
  ( voyageMovementRowApp,
  )
import Support.HspecWaiJa (bodyContainsText, isNotHtmlPage)

spec :: Spec
spec = with (pure voyageMovementRowApp) $ do
  describe "GET /voyages/new/movement-row (T-04 htmx 部分 HTML)" $ do
    it "index 指定なしは「区間 4」を返す (デフォルト)" $
      get "/voyages/new/movement-row"
        `shouldRespondWith` 200
          { matchBody = bodyContainsText "区間 4"
          }

    it "index=5 を指定すると「区間 5」を返す" $
      get "/voyages/new/movement-row?index=5"
        `shouldRespondWith` 200
          { matchBody = bodyContainsText "区間 5"
          }

    it "返却は部分 HTML (<html>/<!DOCTYPE> を含まない)" $ do
      res <- get "/voyages/new/movement-row?index=2"
      let body = LBS.toStrict (simpleBody res)
      liftIO $ body `shouldSatisfy` isNotHtmlPage

    it "movementN* 入力フィールドの命名規約に従う (name 属性に movement2* を含む)" $
      get "/voyages/new/movement-row?index=2"
        `shouldRespondWith` 200
          { matchBody = bodyContainsText "name=\"movement2Departure\""
          }
