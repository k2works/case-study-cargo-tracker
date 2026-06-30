-- | BookingStatus 状態遷移のテスト (IT1-IT4 統合)
module Booking.Domain.Model.State.BookingStatusSpec (spec) where

import Test.Hspec

import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
    bookingStatusToText,
    canTransitionTo,
  )

spec :: Spec
spec = describe "BookingStatus (IT1-IT4)" $ do
  describe "canTransitionTo: 許可される遷移" $ do
    it "Draft -> Submitted" $ canTransitionTo Draft Submitted `shouldBe` True
    it "Submitted -> RouteProposed" $ canTransitionTo Submitted RouteProposed `shouldBe` True
    it "Submitted -> Cancelled" $ canTransitionTo Submitted Cancelled `shouldBe` True
    it "RouteProposed -> RouteAssigned (IT4)" $ canTransitionTo RouteProposed RouteAssigned `shouldBe` True
    it "RouteProposed -> Cancelled" $ canTransitionTo RouteProposed Cancelled `shouldBe` True
    it "RouteAssigned -> Confirmed (US13)" $ canTransitionTo RouteAssigned Confirmed `shouldBe` True
    it "RouteAssigned -> Draft (US11 unlink)" $ canTransitionTo RouteAssigned Draft `shouldBe` True
    it "RouteAssigned -> Cancelled" $ canTransitionTo RouteAssigned Cancelled `shouldBe` True
    it "Confirmed -> Cancelled (US13 キャンセル料適用)" $ canTransitionTo Confirmed Cancelled `shouldBe` True
    it "Confirmed -> Closed" $ canTransitionTo Confirmed Closed `shouldBe` True

  describe "canTransitionTo: 拒否される遷移" $ do
    it "Draft -> Confirmed (直接遷移禁止)" $ canTransitionTo Draft Confirmed `shouldBe` False
    it "Draft -> RouteAssigned (Submitted/RouteProposed 経由必須)" $
      canTransitionTo Draft RouteAssigned `shouldBe` False
    it "Cancelled -> Confirmed (取消後の復帰不可)" $
      canTransitionTo Cancelled Confirmed `shouldBe` False
    it "Cancelled -> Draft (取消後の復帰不可)" $
      canTransitionTo Cancelled Draft `shouldBe` False
    it "Closed -> Confirmed (終了状態は最終)" $
      canTransitionTo Closed Confirmed `shouldBe` False
    it "Confirmed -> RouteAssigned (確定後は経路変更不可)" $
      canTransitionTo Confirmed RouteAssigned `shouldBe` False
    it "同一状態への遷移 (自己ループ) は False" $
      canTransitionTo Draft Draft `shouldBe` False

  describe "全 7 状態 × 7 状態 = 49 ペアの網羅性" $
    it "許可遷移は 10 件、それ以外は 39 件全て False" $ do
      let all_ = [minBound .. maxBound] :: [BookingStatus]
          accepted = [(a, b) | a <- all_, b <- all_, canTransitionTo a b]
      length accepted `shouldBe` 10

  describe "bookingStatusToText (DB CHECK 整合)" $ do
    it "Draft -> DRAFT" $ bookingStatusToText Draft `shouldBe` "DRAFT"
    it "Submitted -> SUBMITTED" $ bookingStatusToText Submitted `shouldBe` "SUBMITTED"
    it "RouteProposed -> ROUTE_PROPOSED" $ bookingStatusToText RouteProposed `shouldBe` "ROUTE_PROPOSED"
    it "RouteAssigned -> ROUTE_ASSIGNED" $ bookingStatusToText RouteAssigned `shouldBe` "ROUTE_ASSIGNED"
    it "Confirmed -> CONFIRMED" $ bookingStatusToText Confirmed `shouldBe` "CONFIRMED"
    it "Cancelled -> CANCELLED" $ bookingStatusToText Cancelled `shouldBe` "CANCELLED"
    it "Closed -> CLOSED" $ bookingStatusToText Closed `shouldBe` "CLOSED"
