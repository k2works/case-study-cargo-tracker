{- | Cargo 集約のテスト (IT1 US04 3.1)

集約ルート Cargo: BookingId / ShipperRef (参照) / RouteSpecification / BookingStatus。
新規貨物予約は Draft 状態で開始し、submitBooking で Submitted に遷移する。
-}
module Booking.Domain.Model.CargoSpec (spec) where

import qualified Data.Text as T
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    cancelBooking,
    confirmBooking,
    linkRoute,
    mkCargo,
    requestRouting,
    submitBooking,
    unlinkRoute,
  )
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId (..),
    mkBookingId,
  )
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef, mkShipperRef)

unsafeBookingId :: BookingId
unsafeBookingId = case mkBookingId "BK-A1B2C3" of
  Right b -> b
  Left _ -> error "test: invalid id"

unsafeShipperId :: ShipperRef
unsafeShipperId = case mkShipperRef "SHP-X1Y2Z3" of
  Right s -> s
  Left _ -> error "test: invalid shipper"

deadline :: UTCTime
deadline = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

spec :: Spec
spec = do
  describe "BookingId" $ do
    it "BK- プレフィックスがないと不正" $
      mkBookingId "A1B2C3" `shouldBe` Left (InvalidBookingId "expected BK-XXXXXX")
    it "プレフィックス後が 6 文字でないと不正" $
      mkBookingId "BK-AB" `shouldBe` Left (InvalidBookingId "expected BK-XXXXXX")
    it "正しいフォーマットは構築できる" $
      mkBookingId "BK-A1B2C3" `shouldBe` Right (BookingId "BK-A1B2C3")

  describe "mkCargo (新規予約)" $ do
    it "Draft 状態で構築される" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      cargoBookingId cargo `shouldBe` unsafeBookingId
      cargoStatus cargo `shouldBe` Draft
      cargoVersion cargo `shouldBe` 1
    it "同じ origin と destination は許される (IT2 で経路設計時に検証)" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      let route = RouteSpecification {origin = o, destination = o, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      cargoStatus cargo `shouldBe` Draft

  describe "submitBooking" $ do
    it "Draft → Submitted に遷移する" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      case submitBooking cargo of
        Right c2 -> do
          cargoStatus c2 `shouldBe` Submitted
          cargoVersion c2 `shouldBe` 2
        Left e -> expectationFailure ("submit failed: " <> show e)
    it "Submitted の貨物は再度 submit できず InvalidStateTransition を返す (H-01)" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      case submitBooking cargo of
        Right c2 -> case submitBooking c2 of
          Left (InvalidStateTransition from to_) -> do
            from `shouldBe` T.pack (show Submitted)
            to_ `shouldBe` T.pack (show Submitted)
          Left other -> expectationFailure ("expected InvalidStateTransition, got " <> show other)
          Right _ -> expectationFailure "submitted を submit して成功した"
        Left e -> expectationFailure ("first submit failed: " <> show e)

  describe "requestRouting (US06)" $ do
    it "Submitted → RouteProposed に遷移し version が +1 される" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      Right submitted <- pure (submitBooking cargo)
      case requestRouting submitted of
        Right c3 -> do
          cargoStatus c3 `shouldBe` RouteProposed
          cargoVersion c3 `shouldBe` 3
        Left e -> expectationFailure ("requestRouting failed: " <> show e)

    it "Draft からの直接引き渡しは InvalidStateTransition" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      case requestRouting cargo of
        Left (InvalidStateTransition fromS toS) -> do
          fromS `shouldBe` "Draft"
          toS `shouldBe` "RouteProposed"
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

    it "RouteProposed の貨物は二重引き渡しできない" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      Right submitted <- pure (submitBooking cargo)
      Right routed <- pure (requestRouting submitted)
      case requestRouting routed of
        Left (InvalidStateTransition _ _) -> pure ()
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

  describe "linkRoute (US11, IT4)" $ do
    let mkRouted = do
          Right o <- pure (mkUnLocode "JPTYO")
          Right d <- pure (mkUnLocode "USNYC")
          let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
              cargo = mkCargo unsafeBookingId unsafeShipperId route
          Right s <- pure (submitBooking cargo)
          Right r <- pure (requestRouting s)
          pure r

    it "RouteProposed -> RouteAssigned に遷移する" $ do
      routed <- mkRouted
      case linkRoute routed of
        Right c -> cargoStatus c `shouldBe` RouteAssigned
        Left e -> expectationFailure (show e)

    it "Draft からの直接 linkRoute は InvalidStateTransition" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let cargo = mkCargo unsafeBookingId unsafeShipperId (RouteSpecification o d deadline)
      case linkRoute cargo of
        Left (InvalidStateTransition _ _) -> pure ()
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

  describe "unlinkRoute (US11, IT4)" $ do
    it "RouteAssigned -> Draft (確定前なら解除可能)" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let cargo = mkCargo unsafeBookingId unsafeShipperId (RouteSpecification o d deadline)
      Right s <- pure (submitBooking cargo)
      Right r <- pure (requestRouting s)
      Right a <- pure (linkRoute r)
      case unlinkRoute a of
        Right c -> cargoStatus c `shouldBe` Draft
        Left e -> expectationFailure (show e)

    it "Submitted からの unlinkRoute は不可" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let cargo = mkCargo unsafeBookingId unsafeShipperId (RouteSpecification o d deadline)
      Right s <- pure (submitBooking cargo)
      case unlinkRoute s of
        Left (InvalidStateTransition _ _) -> pure ()
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

  describe "confirmBooking (US13, IT4)" $ do
    it "RouteAssigned -> Confirmed に遷移する" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let cargo = mkCargo unsafeBookingId unsafeShipperId (RouteSpecification o d deadline)
      Right s <- pure (submitBooking cargo)
      Right r <- pure (requestRouting s)
      Right a <- pure (linkRoute r)
      case confirmBooking a of
        Right c -> cargoStatus c `shouldBe` Confirmed
        Left e -> expectationFailure (show e)

    it "経路紐付け前 (RouteProposed) からの確定は不可" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let cargo = mkCargo unsafeBookingId unsafeShipperId (RouteSpecification o d deadline)
      Right s <- pure (submitBooking cargo)
      Right r <- pure (requestRouting s)
      case confirmBooking r of
        Left (InvalidStateTransition _ _) -> pure ()
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

  describe "cancelBooking (US13, IT4)" $ do
    let mkAt status = do
          Right o <- pure (mkUnLocode "JPTYO")
          Right d <- pure (mkUnLocode "USNYC")
          let cargo = mkCargo unsafeBookingId unsafeShipperId (RouteSpecification o d deadline)
          case status of
            Draft -> pure cargo
            Submitted -> do
              Right s <- pure (submitBooking cargo)
              pure s
            RouteProposed -> do
              Right s <- pure (submitBooking cargo)
              Right r <- pure (requestRouting s)
              pure r
            RouteAssigned -> do
              Right s <- pure (submitBooking cargo)
              Right r <- pure (requestRouting s)
              Right a <- pure (linkRoute r)
              pure a
            Confirmed -> do
              Right s <- pure (submitBooking cargo)
              Right r <- pure (requestRouting s)
              Right a <- pure (linkRoute r)
              Right cf <- pure (confirmBooking a)
              pure cf
            _ -> error "unsupported test status"

    it "Submitted からキャンセル可能" $ do
      c <- mkAt Submitted
      case cancelBooking c of
        Right done -> cargoStatus done `shouldBe` Cancelled
        Left e -> expectationFailure (show e)

    it "RouteProposed からキャンセル可能" $ do
      c <- mkAt RouteProposed
      fmap cargoStatus (cancelBooking c) `shouldBe` Right Cancelled

    it "RouteAssigned からキャンセル可能" $ do
      c <- mkAt RouteAssigned
      fmap cargoStatus (cancelBooking c) `shouldBe` Right Cancelled

    it "Confirmed からキャンセル可能 (キャンセル料は Application 層で算定)" $ do
      c <- mkAt Confirmed
      fmap cargoStatus (cancelBooking c) `shouldBe` Right Cancelled

    it "Draft からのキャンセルは InvalidStateTransition" $ do
      c <- mkAt Draft
      case cancelBooking c of
        Left (InvalidStateTransition _ _) -> pure ()
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

    it "キャンセル後の再キャンセルは InvalidStateTransition" $ do
      c <- mkAt Submitted
      Right cancelled <- pure (cancelBooking c)
      case cancelBooking cancelled of
        Left (InvalidStateTransition _ _) -> pure ()
        other -> expectationFailure ("expected InvalidStateTransition but got " <> show other)

    it "version は遷移ごとに +1 される" $ do
      c <- mkAt Submitted -- version: 1 (mkCargo) -> 2 (submit)
      Right cancelled <- pure (cancelBooking c)
      cargoVersion cancelled `shouldBe` cargoVersion c + 1
