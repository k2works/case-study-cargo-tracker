-- | Estimate 集約 + 配下 VO/Entity のテスト (US01, IT2)
module Estimation.Domain.Model.EstimateSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Estimation.Domain.Model.Estimate
  ( Estimate (..),
    mkEstimate,
  )
import Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate (..),
    mkRouteCandidate,
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateId
  ( EstimateId (..),
    mkEstimateId,
  )
import Cargotracker.Estimation.Domain.Model.Value.EstimateStatus
  ( EstimateStatus (..),
    estimateStatusToText,
    parseEstimateStatus,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

validUuid :: EstimateId
validUuid = case mkEstimateId "550e8400-e29b-41d4-a716-446655440000" of
  Right e -> e
  Left _ -> error "test setup: invalid uuid"

deadlineAt :: UTCTime
deadlineAt = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

rcDirect :: RouteCandidate
rcDirect = case mkRouteCandidate 0 14 100000 ["V0001"] of
  Right c -> c
  Left _ -> error "test setup: invalid candidate"

rcTransit :: RouteCandidate
rcTransit = case mkRouteCandidate 1 18 80000 ["V0001", "V0002"] of
  Right c -> c
  Left _ -> error "test setup: invalid candidate"

spec :: Spec
spec = do
  describe "EstimateId" $ do
    it "正しい UUID は Right" $
      mkEstimateId "550e8400-e29b-41d4-a716-446655440000"
        `shouldBe` Right (EstimateId "550e8400-e29b-41d4-a716-446655440000")
    it "36 文字以外は Left" $
      mkEstimateId "short-id"
        `shouldBe` Left (InvalidBookingId "EstimateId: expected UUID (36 chars)")
    it "ハイフン位置が不正は Left" $
      mkEstimateId "550e8400e29b41d4-a716-446655440000aa"
        `shouldBe` Left (InvalidBookingId "EstimateId: invalid UUID format")
    it "16 進以外の文字を含むと Left" $
      mkEstimateId "ZZZe8400-e29b-41d4-a716-446655440000"
        `shouldBe` Left (InvalidBookingId "EstimateId: invalid UUID format")

  describe "EstimateStatus" $ do
    it "Created/Expired の Text 変換" $ do
      estimateStatusToText Created `shouldBe` "Created"
      estimateStatusToText Expired `shouldBe` "Expired"
    it "parseEstimateStatus は逆変換可能" $ do
      parseEstimateStatus "Created" `shouldBe` Right Created
      parseEstimateStatus "Expired" `shouldBe` Right Expired
    it "未定義文字列は Left" $
      parseEstimateStatus "Pending"
        `shouldBe` Left (InvalidBookingId "unknown EstimateStatus: Pending")

  describe "RouteCandidate" $ do
    it "正しい入力は Right" $ do
      Right rc <- pure (mkRouteCandidate 0 14 100000 ["V0001"])
      rank rc `shouldBe` 0
      transitDays rc `shouldBe` 14
      estimatedCost rc `shouldBe` 100000
      voyageNumbers rc `shouldBe` ["V0001"]
    it "rank が負は Left" $
      mkRouteCandidate (-1) 14 100000 ["V0001"]
        `shouldBe` Left (InvalidBookingId "rank must be >= 0")
    it "transitDays が 0 以下は Left" $
      mkRouteCandidate 0 0 100000 ["V0001"]
        `shouldBe` Left (InvalidBookingId "transitDays must be >= 1")
    it "estimatedCost が負は Left" $
      mkRouteCandidate 0 14 (-1) ["V0001"]
        `shouldBe` Left (InvalidBookingId "estimatedCost must be >= 0")
    it "voyageNumbers が空は Left" $
      mkRouteCandidate 0 14 100000 []
        `shouldBe` Left (InvalidBookingId "voyageNumbers must not be empty")
    it "voyageNumbers に空文字を含むと Left" $
      mkRouteCandidate 0 14 100000 ["V0001", "  "]
        `shouldBe` Left (InvalidBookingId "voyageNumbers must not contain empty strings")

  describe "Estimate" $ do
    it "候補ありの正常入力で Created 状態の集約を構築" $ do
      Right e <-
        pure
          ( mkEstimate
              validUuid
              "SHP-A1B2C3"
              (UnLocode "JPTYO")
              (UnLocode "USNYC")
              deadlineAt
              "GENERAL"
              500.0
              [rcDirect, rcTransit]
          )
      estimateId e `shouldBe` validUuid
      estimateStatus e `shouldBe` Created
      length (routeCandidates e) `shouldBe` 2

    it "候補なし (期限内到達不可) でも Right で構築可能 (空集合)" $ do
      Right e <-
        pure
          ( mkEstimate
              validUuid
              "SHP-A1B2C3"
              (UnLocode "JPTYO")
              (UnLocode "USNYC")
              deadlineAt
              "GENERAL"
              500.0
              []
          )
      routeCandidates e `shouldBe` []
      estimateStatus e `shouldBe` Created

    it "weightKg が 0 以下は Left" $
      mkEstimate
        validUuid
        "SHP-A1B2C3"
        (UnLocode "JPTYO")
        (UnLocode "USNYC")
        deadlineAt
        "GENERAL"
        0.0
        [rcDirect]
        `shouldBe` Left (InvalidBookingId "Estimate.weightKg must be > 0")

    it "RouteCandidate.rank が重複すると Left" $ do
      let dup = case mkRouteCandidate 0 20 90000 ["V0003"] of
            Right c -> c
            Left _ -> error "test setup"
      mkEstimate
        validUuid
        "SHP-A1B2C3"
        (UnLocode "JPTYO")
        (UnLocode "USNYC")
        deadlineAt
        "GENERAL"
        500.0
        [rcDirect, dup]
        `shouldBe` Left (InvalidBookingId "Estimate: route candidate ranks must be unique")
