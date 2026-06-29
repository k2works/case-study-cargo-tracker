{-# LANGUAGE OverloadedStrings #-}

{- | VoyagePageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /voyages/new が航海フォーム入力を受け取り、成功時は航海詳細画面
(`/voyages/:voyageNumber`)、失敗時は `?error=` クエリ付きで /voyages/new
にリダイレクト (303 See Other) することを検証する。
-}
module Routing.Interfaces.VoyagePageApiSpec (spec) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Lazy
import qualified Data.ByteString.Lazy as BSL
import qualified Data.ByteString.Lazy.Char8 as BSL8
import Data.IORef (modifyIORef', newIORef)
import Data.List (isInfixOf)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Network.Wai.Test (simpleBody)
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage, mkVoyage)
import Cargotracker.Routing.Interfaces.VoyagePageApi (voyagePageApp)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Network.Wai (Application)

makeRepo :: IO (VoyageRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Voyage])
  pure
    VoyageRepository
      { findByVoyageNumber = \_ -> pure Nothing
      , saveVoyage = \v -> modifyIORef' ref (v :)
      , updateVoyage = \_ -> pure (Right ())
      , findAllVoyages = pure []
      }

spec :: Spec
spec = do
  specCreate
  specUpdate
  specSearch

specCreate :: Spec
specCreate = with (fmap voyagePageApp makeRepo) $ do
  describe "POST /voyages/new (T-03 PRG)" $ do
    it "正常系は 303 を返し Location が /voyages/:voyageNumber を指す" $
      request
        "POST"
        "/voyages/new"
        [("Content-Type", "application/x-www-form-urlencoded")]
        ( "voyageNumber=V-001"
            <> "&movement1Departure=JPTYO"
            <> "&movement1Arrival=USNYC"
            <> "&movement1DepartureTime=2026-07-01T10%3A00"
            <> "&movement1ArrivalTime=2026-07-15T08%3A00"
        )
        `shouldRespondWith` 303
          { matchHeaders = ["Location" <:> "/voyages/V-001"]
          }

    it "区間 1 の時刻フォーマット不正は 303 + Location が /voyages/new?error=... を指す" $ do
      res <-
        request
          "POST"
          "/voyages/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          ( "voyageNumber=V-001"
              <> "&movement1Departure=JPTYO"
              <> "&movement1Arrival=USNYC"
              <> "&movement1DepartureTime=BAD"
              <> "&movement1ArrivalTime=ALSO_BAD"
          )
      shouldRespondWith (pure res) 303

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 7 1) (secondsToDiffTime 0)

seedVoyage :: Voyage
seedVoyage =
  case mkVoyageNumber "V0001" of
    Right vn ->
      case mkVoyage
        vn
        [ CarrierMovement
            { departureLocation = UnLocode "JPTYO"
            , arrivalLocation = UnLocode "USNYC"
            , departureTime = t0
            , arrivalTime = addUTCTime 86400 t0
            }
        ] of
        Right v -> v
        Left _ -> error "test setup: mkVoyage failed"
    Left _ -> error "test setup: mkVoyageNumber failed"

mkUpdateApp :: Maybe Voyage -> Either DomainError () -> IO Application
mkUpdateApp seed updResult =
  pure
    ( voyagePageApp
        VoyageRepository
          { findByVoyageNumber = \_ -> pure seed
          , saveVoyage = \_ -> pure ()
          , updateVoyage = \_ -> pure updResult
          , findAllVoyages = pure []
          }
    )

validUpdateBody :: Data.ByteString.Lazy.ByteString
validUpdateBody =
  "voyageNumber=V0001"
    <> "&movement1Departure=JPTYO"
    <> "&movement1Arrival=USLAX"
    <> "&movement1DepartureTime=2026-07-01T10%3A00"
    <> "&movement1ArrivalTime=2026-07-15T08%3A00"
    <> "&movement2Departure=USLAX"
    <> "&movement2Arrival=USNYC"
    <> "&movement2DepartureTime=2026-07-15T10%3A00"
    <> "&movement2ArrivalTime=2026-07-20T08%3A00"

specUpdate :: Spec
specUpdate = do
  describe "GET /voyages/:voyageNumber/edit (US25)" $ do
    with (mkUpdateApp (Just seedVoyage) (Right ())) $
      it "既存航海なら 200 を返す" $
        get "/voyages/V0001/edit" `shouldRespondWith` 200

    with (mkUpdateApp Nothing (Right ())) $
      it "未存在は 200 + not-found ページ" $
        get "/voyages/V0999/edit" `shouldRespondWith` 200

    with (mkUpdateApp (Just seedVoyage) (Right ())) $
      it "U-03: 既存航海の edit ページに区間 1 のプリフィル値が含まれる" $ do
        res <- get "/voyages/V0001/edit"
        liftIO $ do
          -- 出発港 (JPTYO) と到着港 (USNYC) が select の selected として埋まる
          let body = BSL8.unpack (simpleBody res)
          ("selected" `isInfixOf` body) `shouldBe` True
          ("JPTYO" `isInfixOf` body) `shouldBe` True
          ("USNYC" `isInfixOf` body) `shouldBe` True
          -- 出発時刻が input value として埋まる (2026-07-01T00:00)
          ("2026-07-01T00:00" `isInfixOf` body) `shouldBe` True

  describe "POST /voyages/:voyageNumber/update (US25 PRG)" $ do
    with (mkUpdateApp (Just seedVoyage) (Right ())) $
      it "正常更新は 303 + Location が /voyages/V0001?flash=updated" $
        request
          "POST"
          "/voyages/V0001/update"
          [("Content-Type", "application/x-www-form-urlencoded")]
          validUpdateBody
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/voyages/V0001?flash=updated"]
            }

    with (mkUpdateApp Nothing (Right ())) $
      it "未存在は 303 + /voyages/new?error=voyage-not-found" $
        request
          "POST"
          "/voyages/V0999/update"
          [("Content-Type", "application/x-www-form-urlencoded")]
          validUpdateBody
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/voyages/new?error=voyage-not-found"]
            }

    with (mkUpdateApp (Just seedVoyage) (Left (ConcurrentModification "V0001"))) $
      it "楽観ロック衝突は 303 + /voyages/V0001/edit?error=concurrent-modification" $
        request
          "POST"
          "/voyages/V0001/update"
          [("Content-Type", "application/x-www-form-urlencoded")]
          validUpdateBody
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/voyages/V0001/edit?error=concurrent-modification"]
            }

specSearch :: Spec
specSearch = do
  describe "GET /voyages/search (US07)" $ do
    with (mkUpdateApp (Just seedVoyage) (Right ())) $
      it "クエリ未指定はフォームのみ 200 を返す" $
        get "/voyages/search" `shouldRespondWith` 200

    with (mkSearchApp [seedVoyage]) $
      it "条件にマッチする航海が結果テーブルに表示される" $ do
        res <-
          get
            ( "/voyages/search?from=JPTYO&to=USNYC"
                <> "&from_date=2026-07-01&to_date=2026-12-31"
            )
        liftIO $ do
          let body = bodyAsText res
          T.isInfixOf "V0001" body `shouldBe` True
          T.isInfixOf "検索結果" body `shouldBe` True

    with (mkSearchApp []) $
      it "該当 0 件は alert-warning メッセージを表示" $ do
        res <-
          get
            ( "/voyages/search?from=JPTYO&to=USNYC"
                <> "&from_date=2026-07-01&to_date=2026-12-31"
            )
        liftIO $ do
          let body = bodyAsText res
          T.isInfixOf "該当する航海がありません" body `shouldBe` True

    with (mkSearchApp []) $
      it "出発期間逆順は 200 + バリデーションメッセージ" $ do
        res <-
          get
            ( "/voyages/search?from=JPTYO&to=USNYC"
                <> "&from_date=2026-12-31&to_date=2026-07-01"
            )
        liftIO $ do
          let body = bodyAsText res
          T.isInfixOf "出発期間" body `shouldBe` True
  where
    bodyAsText = TE.decodeUtf8 . BSL.toStrict . simpleBody

mkSearchApp :: [Voyage] -> IO Application
mkSearchApp voys =
  pure
    ( voyagePageApp
        VoyageRepository
          { findByVoyageNumber = \_ -> pure Nothing
          , saveVoyage = \_ -> pure ()
          , updateVoyage = \_ -> pure (Right ())
          , findAllVoyages = pure voys
          }
    )
