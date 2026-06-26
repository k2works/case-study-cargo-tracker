{- | Cargo 集約のテスト (IT1 US04 3.1)

集約ルート Cargo: BookingId / ShipperId (参照) / RouteSpecification / BookingStatus。
新規貨物予約は Draft 状態で開始し、submitBooking で Submitted に遷移する。
-}
module Booking.Domain.Model.CargoSpec (spec) where

import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
    submitBooking,
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
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId, mkShipperId)

unsafeBookingId :: BookingId
unsafeBookingId = case mkBookingId "BK-A1B2C3" of
  Right b -> b
  Left _ -> error "test: invalid id"

unsafeShipperId :: ShipperId
unsafeShipperId = case mkShipperId "SHP-X1Y2Z3" of
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
    it "Submitted の貨物は再度 submit できない" $ do
      Right o <- pure (mkUnLocode "JPTYO")
      Right d <- pure (mkUnLocode "USNYC")
      let route = RouteSpecification {origin = o, destination = d, arrivalDeadline = deadline}
          cargo = mkCargo unsafeBookingId unsafeShipperId route
      case submitBooking cargo of
        Right c2 -> case submitBooking c2 of
          Left _ -> pure ()
          Right _ -> expectationFailure "submitted を submit して成功した"
        Left e -> expectationFailure ("first submit failed: " <> show e)
