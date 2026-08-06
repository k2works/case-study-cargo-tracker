-- | markInExceptionByTrackingNumber + checkTransitionForException のテスト (ADR-0014 Phase 1)
module Tracking.Application.MarkInExceptionSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Application.Ports
  ( TrackingRepository (..),
    checkTransitionForException,
    markInExceptionByTrackingNumber,
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    initialActivity,
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber,
    mkTrackingNumber,
  )

sampleTn :: TrackingNumber
sampleTn = case mkTrackingNumber "TR000001" of
  Right t -> t
  Left _ -> error "test setup: invalid tracking number"

makeRepo :: [TrackingActivity] -> IO (TrackingRepository IO, IO [TrackingActivity])
makeRepo initial = do
  ref <- newIORef initial
  let repo =
        TrackingRepository
          { saveTracking = \_ -> pure (Right ())
          , findByBookingId = \bid -> do
              xs <- readIORef ref
              pure (case [a | a <- xs, taBookingId a == bid] of (x : _) -> Just x; [] -> Nothing)
          , findByTrackingNumber = \tn -> do
              xs <- readIORef ref
              pure (case [a | a <- xs, taTrackingNumber a == tn] of (x : _) -> Just x; [] -> Nothing)
          , updateTransportStatus = \bid st -> do
              modifyIORef' ref (map (\a -> if taBookingId a == bid then a {taTransportStatus = st} else a))
              pure (Right ())
          }
  pure (repo, readIORef ref)

activityInStatus :: TransportStatus -> TrackingActivity
activityInStatus st = (initialActivity sampleTn "BK-A1B2C3") {taTransportStatus = st}

spec :: Spec
spec = do
  describe "checkTransitionForException (ADR-0014, Pure)" $ do
    it "TsReceived / TsLoaded / TsOnboardCarrier / TsUnloaded / TsAwaitingClaim / TsUnknown からは遷移可" $ do
      checkTransitionForException TsReceived `shouldBe` Right ()
      checkTransitionForException TsLoaded `shouldBe` Right ()
      checkTransitionForException TsOnboardCarrier `shouldBe` Right ()
      checkTransitionForException TsUnloaded `shouldBe` Right ()
      checkTransitionForException TsAwaitingClaim `shouldBe` Right ()
      checkTransitionForException TsUnknown `shouldBe` Right ()

    it "TsNotReceived からは遷移禁止" $
      checkTransitionForException TsNotReceived
        `shouldBe` Left (InvalidTrackingTransition "TsNotReceived" "TsInException")

    it "TsClaimed からは遷移禁止 (引取完了後は例外扱わず)" $
      checkTransitionForException TsClaimed
        `shouldBe` Left (InvalidTrackingTransition "TsClaimed" "TsInException")

    it "TsInException からは遷移禁止 (二重例外は追記型で管理)" $
      checkTransitionForException TsInException
        `shouldBe` Left (InvalidTrackingTransition "TsInException" "TsInException")

  describe "markInExceptionByTrackingNumber (ADR-0014 Phase 1)" $ do
    it "正常系: TsReceived → TsInException に遷移" $ do
      (repo, getAll) <- makeRepo [activityInStatus TsReceived]
      result <- markInExceptionByTrackingNumber repo "TR000001"
      result `shouldBe` Right ()
      xs <- getAll
      case xs of
        [a] -> taTransportStatus a `shouldBe` TsInException
        _ -> expectationFailure "expected 1 activity"

    it "不正な tracking number 形式は InvalidTrackingNumberFormat" $ do
      (repo, _) <- makeRepo []
      result <- markInExceptionByTrackingNumber repo "invalid"
      case result of
        Left (InvalidTrackingNumberFormat _) -> pure ()
        other -> expectationFailure ("expected InvalidTrackingNumberFormat, got " <> show other)

    it "存在しない tracking number は TrackingNotFound" $ do
      (repo, _) <- makeRepo []
      result <- markInExceptionByTrackingNumber repo "TR000001"
      result `shouldBe` Left (TrackingNotFound "TR000001")

    it "TsClaimed 状態からの遷移は InvalidTrackingTransition、状態更新されない" $ do
      (repo, getAll) <- makeRepo [activityInStatus TsClaimed]
      result <- markInExceptionByTrackingNumber repo "TR000001"
      result `shouldBe` Left (InvalidTrackingTransition "TsClaimed" "TsInException")
      xs <- getAll
      case xs of
        [a] -> taTransportStatus a `shouldBe` TsClaimed
        _ -> expectationFailure "expected 1 activity"

    it "TsNotReceived 状態からの遷移も InvalidTrackingTransition" $ do
      (repo, _) <- makeRepo [activityInStatus TsNotReceived]
      result <- markInExceptionByTrackingNumber repo "TR000001"
      result `shouldBe` Left (InvalidTrackingTransition "TsNotReceived" "TsInException")
