{-# LANGUAGE OverloadedStrings #-}

{- | TxRunner (トランザクション境界抽象) の単体テスト (T5-03, ADR-0012, IT6)

Application 層はポート実装の内部トランザクションではなく、`TxRunner` に
包まれた副作用ブロック単位でコミット/ロールバックできる。ここでは Postgres 依存の
`newPostgresTxRunner` 経路とは切り分け、`noTxRunner` (テスト用パススルー) と
「例外が伝播しブロック内アクションが失敗を返さない」ことのみを検証する。

Postgres の実 Tx 挙動は Testcontainers ベースの統合テストで別途カバーする。
-}
module Shared.Application.TxRunnerSpec (spec) where

import Control.Exception (SomeException, throwIO, try)
import Data.IORef (modifyIORef', newIORef, readIORef)
import Test.Hspec

import Cargotracker.Shared.Application.TxRunner
  ( TxRunner (..),
    noTxRunner,
  )

spec :: Spec
spec = describe "TxRunner (T5-03 ADR-0012)" $ do
  it "noTxRunner はブロック内アクションの結果を素通しする" $ do
    result <- runInTx noTxRunner (pure (42 :: Int))
    result `shouldBe` 42

  it "noTxRunner は副作用も素通しする" $ do
    ref <- newIORef (0 :: Int)
    _ <- runInTx noTxRunner (modifyIORef' ref (+ 1))
    readIORef ref `shouldReturn` 1

  it "noTxRunner ブロック内の例外は伝播する" $ do
    outcome <- try (runInTx noTxRunner (throwIO (userError "boom")))
    case (outcome :: Either SomeException ()) of
      Left _ -> pure ()
      Right _ -> expectationFailure "expected exception to propagate"

  it "自作 TxRunner を差し込むと前後にフックできる" $ do
    ref <- newIORef ([] :: [String])
    let auditRunner =
          TxRunner
            ( \action -> do
                modifyIORef' ref ("begin" :)
                r <- action
                modifyIORef' ref ("commit" :)
                pure r
            )
    result <- runInTx auditRunner (modifyIORef' ref ("body" :) >> pure "ok")
    result `shouldBe` "ok"
    trace <- readIORef ref
    reverse trace `shouldBe` ["begin", "body", "commit"]
