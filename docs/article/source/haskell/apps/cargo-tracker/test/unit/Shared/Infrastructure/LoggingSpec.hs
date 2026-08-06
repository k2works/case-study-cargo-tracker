{-# LANGUAGE OverloadedStrings #-}

-- | Cargotracker.Shared.Infrastructure.Logging のテスト (T6-07, IT7 / H-04, IT8)
module Shared.Infrastructure.LoggingSpec (spec) where

import Control.Exception (ErrorCall (..), throwIO, try)
import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Infrastructure.Logging
  ( newCorrelationId,
    withCorrelationId,
  )

spec :: Spec
spec = describe "Cargotracker.Shared.Infrastructure.Logging" $ do
  describe "newCorrelationId (T6-07 UUID v4)" $ do
    it "36 文字の UUID 形式を返す (8-4-4-4-12)" $ do
      cid <- newCorrelationId
      T.length cid `shouldBe` 36
      T.count "-" cid `shouldBe` 4

    it "呼出ごとに異なる ID を生成する (衝突しない)" $ do
      c1 <- newCorrelationId
      c2 <- newCorrelationId
      c1 `shouldNotBe` c2

  describe "withCorrelationId (H-04, IT8)" $ do
    it "正常時は action の結果を返す" $ do
      r <- withCorrelationId "cid-ok" (pure (42 :: Int))
      r `shouldBe` 42

    it "action が例外を投げても finally で最終処理が走り例外は再送出される" $ do
      ref <- newIORef (0 :: Int)
      let action = do
            modifyIORef' ref (+ 1)
            _ <- throwIO (ErrorCall "boom")
            pure ()
      result <- try (withCorrelationId "cid-err" action) :: IO (Either ErrorCall ())
      case result of
        Left (ErrorCall m) -> m `shouldBe` "boom"
        Right () -> expectationFailure "expected exception to propagate"
      -- action は 1 回実行済 (finally は end ログのみで action を再実行しない)
      readIORef ref `shouldReturn` 1
