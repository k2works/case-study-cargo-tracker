{- | TrackingActivity.updateStateManually のテスト (US17, IT7)

Tracker/Admin による手動状態更新の Domain 純粋関数。
状態遷移と監査ログ (TrackingStateAudit) を副産物として返す。
-}
module Tracking.Domain.Model.UpdateStateManuallySpec (spec) where

import Data.Time (UTCTime, fromGregorian, secondsToDiffTime)
import qualified Data.Time as Time
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    initialActivity,
    updateStateManually,
  )
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit (TrackingStateAudit (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber,
    mkTrackingNumber,
  )

fixedNow :: UTCTime
fixedNow =
  Time.UTCTime (fromGregorian 2026 7 3) (secondsToDiffTime 3600)

sampleTn :: TrackingNumber
sampleTn =
  case mkTrackingNumber "TR000001" of
    Right t -> t
    Left _ -> error "test setup: invalid tracking number"

sampleActivity :: TrackingActivity
sampleActivity = initialActivity sampleTn "BK-A1B2C3"

spec :: Spec
spec = describe "updateStateManually (US17, IT7)" $ do
  it "同じ状態への遷移は StateAlreadyMatches" $ do
    let result = updateStateManually TsNotReceived "確認" "user-1" fixedNow sampleActivity
    result `shouldBe` Left (StateAlreadyMatches "TsNotReceived")

  it "空文字の理由は ManualUpdateReasonRequired" $ do
    let result = updateStateManually TsReceived "" "user-1" fixedNow sampleActivity
    result `shouldBe` Left ManualUpdateReasonRequired

  it "空白のみの理由も ManualUpdateReasonRequired" $ do
    let result = updateStateManually TsReceived "   " "user-1" fixedNow sampleActivity
    result `shouldBe` Left ManualUpdateReasonRequired

  it "正常系: 状態遷移と監査ログ両方を返し version が +1" $ do
    case updateStateManually TsClaimed "港湾で目視確認" "tracker-42" fixedNow sampleActivity of
      Right (updated, audit) -> do
        taTransportStatus updated `shouldBe` TsClaimed
        taVersion updated `shouldBe` taVersion sampleActivity + 1
        taTrackingNumber updated `shouldBe` sampleTn
        tsaPreviousStatus audit `shouldBe` TsNotReceived
        tsaNewStatus audit `shouldBe` TsClaimed
        tsaReason audit `shouldBe` "港湾で目視確認"
        tsaChangedBy audit `shouldBe` "tracker-42"
        tsaChangedAt audit `shouldBe` fixedNow
        tsaTrackingNumber audit `shouldBe` sampleTn
      Left err -> expectationFailure ("expected Right, got " <> show err)

  it "任意の非空理由 + 別ユーザーで異なる audit が構築される" $ do
    case updateStateManually TsInException "例外検知" "admin-1" fixedNow sampleActivity of
      Right (updated, audit) -> do
        taTransportStatus updated `shouldBe` TsInException
        tsaChangedBy audit `shouldBe` "admin-1"
        tsaReason audit `shouldBe` "例外検知"
      Left err -> expectationFailure ("expected Right, got " <> show err)
