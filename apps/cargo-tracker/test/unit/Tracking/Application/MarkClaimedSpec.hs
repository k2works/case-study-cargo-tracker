{-# LANGUAGE OverloadedStrings #-}

{- | Cross-BC helper markClaimedByBookingId の単体テスト (T5-04, ADR-0012, IT6)

Handling BC の VerifyClaimAndRegisterCommand が Tx 境界内で呼び出す
Tracking BC 側のヘルパ。booking_id を業務キーに TrackingActivity を検索し、
TransportStatus を TsClaimed に更新する副作用を持つ純粋な IO 関数。
-}
module Tracking.Application.MarkClaimedSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Application.Ports
  ( TrackingRepository (..),
    markClaimedByBookingId,
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity (TrackingActivity (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (unsafeTrackingNumber)

sampleActivity :: TrackingActivity
sampleActivity =
  TrackingActivity
    { taTrackingNumber = unsafeTrackingNumber (T.replicate 8 "1")
    , taBookingId = "BK-A1B2C3"
    , taTransportStatus = TsAwaitingClaim
    , taVersion = 3
    }

spec :: Spec
spec = describe "markClaimedByBookingId (T5-04)" $ do
  it "TrackingActivity が存在すれば updateTransportStatus を TsClaimed で呼ぶ" $ do
    log_ <- newIORef ([] :: [(T.Text, TransportStatus)])
    let repo =
          TrackingRepository
            { saveTracking = \_ -> pure (Right ())
            , findByBookingId = \_ -> pure (Just sampleActivity)
            , findByTrackingNumber = \_ -> pure Nothing
            , updateTransportStatus = \bid s -> do
                modifyIORef' log_ ((bid, s) :)
                pure (Right ())
            }
    result <- markClaimedByBookingId repo "BK-A1B2C3"
    result `shouldBe` Right ()
    calls <- readIORef log_
    calls `shouldBe` [("BK-A1B2C3", TsClaimed)]

  it "TrackingActivity が存在しなければ HandlingBookingNotFound、updateTransportStatus は呼ばれない" $ do
    log_ <- newIORef ([] :: [(T.Text, TransportStatus)])
    let repo =
          TrackingRepository
            { saveTracking = \_ -> pure (Right ())
            , findByBookingId = \_ -> pure Nothing
            , findByTrackingNumber = \_ -> pure Nothing
            , updateTransportStatus = \bid s -> do
                modifyIORef' log_ ((bid, s) :)
                pure (Right ())
            }
    result <- markClaimedByBookingId repo "BK-XXXXXX"
    result `shouldBe` Left (HandlingBookingNotFound "BK-XXXXXX")
    calls <- readIORef log_
    calls `shouldBe` []

  it "updateTransportStatus が失敗した場合そのエラーが伝播する" $ do
    let repo =
          TrackingRepository
            { saveTracking = \_ -> pure (Right ())
            , findByBookingId = \_ -> pure (Just sampleActivity)
            , findByTrackingNumber = \_ -> pure Nothing
            , updateTransportStatus = \_ _ ->
                pure (Left (ConcurrentModification "BK-A1B2C3"))
            }
    result <- markClaimedByBookingId repo "BK-A1B2C3"
    result `shouldBe` Left (ConcurrentModification "BK-A1B2C3")
