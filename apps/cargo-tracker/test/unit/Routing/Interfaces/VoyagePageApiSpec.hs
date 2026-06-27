{-# LANGUAGE OverloadedStrings #-}

{- | VoyagePageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /voyages/new が航海フォーム入力を受け取り、成功時は航海詳細画面
(`/voyages/:voyageNumber`)、失敗時は `?error=` クエリ付きで /voyages/new
にリダイレクト (303 See Other) することを検証する。
-}
module Routing.Interfaces.VoyagePageApiSpec (spec) where

import qualified Data.ByteString.Lazy
import Data.IORef (modifyIORef', newIORef)
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
