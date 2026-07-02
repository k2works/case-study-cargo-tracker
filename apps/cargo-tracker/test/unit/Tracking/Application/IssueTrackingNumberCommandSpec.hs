{-# LANGUAGE OverloadedStrings #-}

{- | IssueTrackingNumberCommand の単体テスト (T5-08, IT6)

観点:
- 冪等: 既発行なら既存 TrackingNumber を返す
- 追跡番号形式不正 → Domain エラー
- 未発行なら新規保存し発行成功
- Repository の永続化失敗を伝播
- 副作用検証: saveTracking が期待した引数で呼ばれる (IORef spy)
-}
module Tracking.Application.IssueTrackingNumberCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus (TransportStatus (..))
import Cargotracker.Tracking.Application.IssueTrackingNumberCommand
  ( IssueTrackingNumberInput (..),
    execute,
  )
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Domain.Model.TrackingActivity (TrackingActivity (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber (..),
    unsafeTrackingNumber,
  )

--------------------------------------------------------------------------------
-- フィクスチャ
--------------------------------------------------------------------------------

validTn :: T.Text
validTn = "TR12345A"

existingActivity :: TrackingActivity
existingActivity =
  TrackingActivity
    { taTrackingNumber = unsafeTrackingNumber validTn
    , taBookingId = "BK-A1B2C3"
    , taTransportStatus = TsNotReceived
    , taVersion = 1
    }

emptyRepo :: TrackingRepository IO
emptyRepo =
  TrackingRepository
    { saveTracking = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure Nothing
    , findByTrackingNumber = \_ -> pure Nothing
    , updateTransportStatus = \_ _ -> pure (Right ())
    }

sampleInput :: IssueTrackingNumberInput
sampleInput =
  IssueTrackingNumberInput
    { inputBookingId = "BK-A1B2C3"
    , inputTrackingNumberText = validTn
    }

spec :: Spec
spec = describe "IssueTrackingNumberCommand.execute (T5-08)" $ do
  it "未発行の予約に対して新規追跡番号を発行する" $ do
    result <- execute emptyRepo sampleInput
    result `shouldBe` Right (unsafeTrackingNumber validTn)

  it "既発行なら既存 TrackingNumber を返す (冪等)" $ do
    let repo = emptyRepo {findByBookingId = \_ -> pure (Just existingActivity)}
    result <- execute repo sampleInput
    result `shouldBe` Right (unsafeTrackingNumber validTn)

  it "追跡番号形式が不正なら DomainError" $ do
    let invalidInput = sampleInput {inputTrackingNumberText = "short"}
    result <- execute emptyRepo invalidInput
    result `shouldBe` Left (InvalidTrackingNumberFormat "short")

  it "Repository の saveTracking 失敗が伝播する" $ do
    let repo = emptyRepo {saveTracking = \_ -> pure (Left (ConcurrentModification "BK-A1B2C3"))}
    result <- execute repo sampleInput
    result `shouldBe` Left (ConcurrentModification "BK-A1B2C3")

  it "副作用検証: saveTracking が期待した TrackingActivity で呼ばれる" $ do
    ref <- newIORef ([] :: [TrackingActivity])
    let repo =
          emptyRepo
            { saveTracking = \a -> modifyIORef' ref (a :) >> pure (Right ())
            }
    _ <- execute repo sampleInput
    saves <- readIORef ref
    length saves `shouldBe` 1
    let saved = head saves
    unTrackingNumber (taTrackingNumber saved) `shouldBe` validTn
    taBookingId saved `shouldBe` "BK-A1B2C3"
    taTransportStatus saved `shouldBe` TsNotReceived
    taVersion saved `shouldBe` 0
