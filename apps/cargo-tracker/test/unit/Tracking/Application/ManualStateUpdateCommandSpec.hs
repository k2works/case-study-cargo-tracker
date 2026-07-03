{- | ManualStateUpdateCommand のテスト (US17, IT7)

Application 層フロー: findByTrackingNumber → updateStateManually (Domain) →
updateTransportStatus + saveAudit の順序を in-memory ports で検証する。
-}
module Tracking.Application.ManualStateUpdateCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef, writeIORef)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, fromGregorian, secondsToDiffTime)
import qualified Data.Time as Time
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Application.ManualStateUpdateCommand
  ( ManualStateUpdateInput (..),
    execute,
  )
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Application.TrackingStateAuditPorts
  ( TrackingStateAuditRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    initialActivity,
  )
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit (TrackingStateAudit (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber,
    mkTrackingNumber,
  )

fixedNow :: UTCTime
fixedNow = Time.UTCTime (fromGregorian 2026 7 3) (secondsToDiffTime 7200)

sampleTn :: TrackingNumber
sampleTn =
  case mkTrackingNumber "TR000001" of
    Right t -> t
    Left _ -> error "test setup: invalid tracking number"

sampleActivity :: TrackingActivity
sampleActivity = initialActivity sampleTn "BK-A1B2C3"

makeRepos ::
  [TrackingActivity] ->
  IO (TrackingRepository IO, TrackingStateAuditRepository IO, IO [TrackingActivity], IO [TrackingStateAudit])
makeRepos initialActivities = do
  activityRef <- newIORef initialActivities
  auditRef <- newIORef ([] :: [TrackingStateAudit])
  let trackingRepo =
        TrackingRepository
          { saveTracking = \_ -> pure (Right ())
          , findByBookingId = \bid -> do
              xs <- readIORef activityRef
              pure (case [a | a <- xs, taBookingId a == bid] of (x : _) -> Just x; [] -> Nothing)
          , findByTrackingNumber = \tn -> do
              xs <- readIORef activityRef
              pure (case [a | a <- xs, taTrackingNumber a == tn] of (x : _) -> Just x; [] -> Nothing)
          , updateTransportStatus = \bid st -> do
              modifyIORef' activityRef (map (\a -> if taBookingId a == bid then a {taTransportStatus = st} else a))
              pure (Right ())
          }
      auditRepo =
        TrackingStateAuditRepository
          { saveAudit = \a -> do
              modifyIORef' auditRef (a :)
              pure (Right ())
          , findAuditsByTrackingNumber = \_ -> readIORef auditRef
          }
  pure (trackingRepo, auditRepo, readIORef activityRef, readIORef auditRef)

sampleInput :: Text -> TransportStatus -> Text -> ManualStateUpdateInput
sampleInput tn st reason =
  ManualStateUpdateInput
    { inputTrackingNumberText = tn
    , inputNewStatus = st
    , inputReason = reason
    , inputChangedBy = "tracker-1"
    , inputNow = fixedNow
    }

spec :: Spec
spec = describe "ManualStateUpdateCommand.execute (US17, IT7)" $ do
  it "存在しない TrackingNumber は TrackingNotFound を返す" $ do
    (tr, ar, _, _) <- makeRepos []
    result <- execute tr ar (sampleInput "TR000001" TsClaimed "確認")
    result `shouldBe` Left (TrackingNotFound "TR000001")

  it "不正な TrackingNumber 形式は InvalidTrackingNumberFormat を返す" $ do
    (tr, ar, _, _) <- makeRepos []
    result <- execute tr ar (sampleInput "invalid" TsClaimed "確認")
    case result of
      Left (InvalidTrackingNumberFormat _) -> pure ()
      other -> expectationFailure ("expected InvalidTrackingNumberFormat, got " <> show other)

  it "同一状態への遷移は StateAlreadyMatches" $ do
    (tr, ar, getActivities, getAudits) <- makeRepos [sampleActivity]
    result <- execute tr ar (sampleInput "TR000001" TsNotReceived "no change")
    result `shouldBe` Left (StateAlreadyMatches "TsNotReceived")
    audits <- getAudits
    length audits `shouldBe` 0
    activities <- getActivities
    case activities of
      [a] -> taTransportStatus a `shouldBe` TsNotReceived
      _ -> expectationFailure "expected 1 activity"

  it "空文字理由は ManualUpdateReasonRequired、副作用は発生しない" $ do
    (tr, ar, _, getAudits) <- makeRepos [sampleActivity]
    result <- execute tr ar (sampleInput "TR000001" TsClaimed "")
    result `shouldBe` Left ManualUpdateReasonRequired
    audits <- getAudits
    length audits `shouldBe` 0

  it "正常系: 状態更新と監査ログ両方が保存される" $ do
    (tr, ar, getActivities, getAudits) <- makeRepos [sampleActivity]
    result <- execute tr ar (sampleInput "TR000001" TsClaimed "港湾で目視確認")
    result `shouldBe` Right ()
    activities <- getActivities
    case activities of
      [a] -> taTransportStatus a `shouldBe` TsClaimed
      _ -> expectationFailure "expected 1 activity"
    audits <- getAudits
    case audits of
      [a] -> do
        tsaPreviousStatus a `shouldBe` TsNotReceived
        tsaNewStatus a `shouldBe` TsClaimed
        tsaReason a `shouldBe` "港湾で目視確認"
        tsaChangedBy a `shouldBe` "tracker-1"
      _ -> expectationFailure "expected 1 audit"

  it "TrackingActivity の永続化失敗時は saveAudit を呼ばない" $ do
    activityRef <- newIORef [sampleActivity]
    auditRef <- newIORef ([] :: [TrackingStateAudit])
    let tr =
          TrackingRepository
            { saveTracking = \_ -> pure (Right ())
            , findByBookingId = \_ -> pure Nothing
            , findByTrackingNumber = \tn -> do
                xs <- readIORef activityRef
                pure (case [a | a <- xs, taTrackingNumber a == tn] of (x : _) -> Just x; [] -> Nothing)
            , updateTransportStatus = \bid _ -> pure (Left (ConcurrentModification bid))
            }
        ar =
          TrackingStateAuditRepository
            { saveAudit = \a -> do
                modifyIORef' auditRef (a :)
                pure (Right ())
            , findAuditsByTrackingNumber = \_ -> readIORef auditRef
            }
    result <- execute tr ar (sampleInput "TR000001" TsClaimed "更新")
    case result of
      Left (ConcurrentModification bid) -> T.unpack bid `shouldBe` "BK-A1B2C3"
      other -> expectationFailure ("expected ConcurrentModification, got " <> show other)
    audits <- readIORef auditRef
    length audits `shouldBe` 0
    writeIORef activityRef [sampleActivity]
