{- | RegisterShipperCommand のテスト (IT1 US02/US03 2.2)

Application 層: 入力 (Text 群) を受け取り、値オブジェクトを構築・検証し、
重複チェックを行ったうえで Shipper 集約を生成・永続化する。

ポート:
- ShipperRepository: save / findByEmail (重複検出用)

US02 = Individual、US03 = Corporate を同コマンドの sum type で扱う。
-}
module Shipper.Application.RegisterShipperCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Application.RegisterShipperCommand
  ( RegisterShipperInput (..),
    ShipperKindInput (..),
    execute,
  )
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank (..),
    Shipper (..),
    ShipperKind (..),
  )

-- In-memory フェイク Repository
makeRepo :: [Shipper] -> IO (ShipperRepository IO, IO [Shipper])
makeRepo initial = do
  ref <- newIORef initial
  let r =
        ShipperRepository
          { findByContactEmail = \e -> do
              xs <- readIORef ref
              pure
                ( case [s | s <- xs, shipperEmail s == e] of
                    (x : _) -> Just x
                    [] -> Nothing
                )
          , findById = \sid -> do
              xs <- readIORef ref
              pure (case [s | s <- xs, shipperId s == sid] of (x : _) -> Just x; [] -> Nothing)
          , save = \s -> modifyIORef' ref (s :)
          , searchByQuery = \_ -> readIORef ref
          }
  pure (r, readIORef ref)

inputIndividual :: RegisterShipperInput
inputIndividual =
  RegisterShipperInput
    { inputId = "SHP-A1B2C3"
    , inputName = "山田 太郎"
    , inputEmail = "alice@example.com"
    , inputAddress = "東京都港区芝公園 4-2-8"
    , inputKind = InputIndividual
    }

inputCorporate :: RegisterShipperInput
inputCorporate =
  RegisterShipperInput
    { inputId = "SHP-D4E5F6"
    , inputName = "株式会社あいうえお"
    , inputEmail = "corp@example.com"
    , inputAddress = "東京都千代田区丸の内 1-1"
    , inputKind = InputCorporate "1234567890123" Gold
    }

spec :: Spec
spec = do
  describe "execute (US02 個人)" $ do
    it "新規個人荷主を保存する" $ do
      (repo, get) <- makeRepo []
      result <- execute repo inputIndividual
      case result of
        Right s -> do
          shipperKind s `shouldBe` Individual
          saved <- get
          length saved `shouldBe` 1
        Left e -> expectationFailure ("expected Right but got " <> show e)

  describe "execute (US03 法人)" $ do
    it "新規法人荷主を保存する" $ do
      (repo, _) <- makeRepo []
      result <- execute repo inputCorporate
      case result of
        Right s -> case shipperKind s of
          Corporate _ Gold -> pure ()
          other -> expectationFailure ("expected Corporate _ Gold but got " <> show other)
        Left e -> expectationFailure ("expected Right but got " <> show e)

  describe "execute (バリデーション)" $ do
    it "ShipperId が不正なら InvalidShipperId" $ do
      (repo, _) <- makeRepo []
      result <- execute repo inputIndividual {inputId = "WRONG"}
      result `shouldBe` Left (InvalidShipperId "expected SHP-XXXXXX")

    it "Email が不正なら InvalidShipperId (Shipper コンテキスト)" $ do
      (repo, _) <- makeRepo []
      result <- execute repo inputIndividual {inputEmail = "no-at"}
      result `shouldBe` Left (InvalidShipperId "invalid email")

    it "Address が空なら InvalidShipperId" $ do
      (repo, _) <- makeRepo []
      result <- execute repo inputIndividual {inputAddress = ""}
      result `shouldBe` Left (InvalidShipperId "empty address")

    it "法人番号が 13 桁でないと InvalidShipperId" $ do
      (repo, _) <- makeRepo []
      result <-
        execute
          repo
          inputCorporate
            { inputKind = InputCorporate "INVALID" Silver
            }
      result `shouldBe` Left (InvalidShipperId "expected 13 digits")

  describe "execute (重複検出)" $ do
    it "同じメールが既存なら ShipperNotFound (誤用しない、別エラー)" $ do
      -- まず 1 件 save
      (repo, _) <- makeRepo []
      _ <- execute repo inputIndividual
      -- 同じメールで再登録 → 重複
      result <- execute repo inputIndividual {inputId = "SHP-X9Y8Z7"}
      case result of
        Left (ConcurrentModification _) -> pure ()
        other -> expectationFailure ("expected ConcurrentModification but got " <> show other)
