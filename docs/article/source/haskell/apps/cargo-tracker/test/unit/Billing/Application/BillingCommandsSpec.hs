{-# LANGUAGE OverloadedStrings #-}

{- | Billing Application 層コマンドのユースケーステスト (US23, IT8)

IORef ベースの fake ポートで以下を検証する:

- GenerateInvoiceCommand: Delivered 前提 / 1 予約 1 請求 / 割引適用 / 通知発火
- IssuePaymentCommand / ConfirmPaymentCommand: 発行 → 照合 → Cargo.Settled 連動
- OverdueCheckCommand: 期限超過のみ遷移 + 未払い通知
- ConfirmPayment 成功時のみ markSettledByBookingId が発火 (spy、T7-C と同型)
-}
module Billing.Application.BillingCommandsSpec (spec) where

import Control.Monad (void)
import Data.IORef (IORef, modifyIORef', newIORef, readIORef)
import Data.Maybe (isJust)
import Data.Text (Text)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Billing.Application.ConfirmPaymentCommand
  ( ConfirmPaymentInput (..),
  )
import qualified Cargotracker.Billing.Application.ConfirmPaymentCommand as Confirm
import Cargotracker.Billing.Application.GenerateInvoiceCommand
  ( GenerateInvoiceInput (..),
  )
import qualified Cargotracker.Billing.Application.GenerateInvoiceCommand as Generate
import Cargotracker.Billing.Application.IssuePaymentCommand
  ( IssuePaymentInput (..),
  )
import qualified Cargotracker.Billing.Application.IssuePaymentCommand as Issue
import Cargotracker.Billing.Application.OverdueCheckCommand
  ( OverdueCheckInput (..),
  )
import qualified Cargotracker.Billing.Application.OverdueCheckCommand as Overdue
import Cargotracker.Billing.Application.Ports
  ( BillingNotificationPort (..),
    BookingCrossBcPort (..),
    ConfirmedCost (..),
    InvoiceRepository (..),
    PaymentGateway (..),
    PricingCrossBcPort (..),
  )
import Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    unBillingBookingId,
    unInvoiceId,
  )
import Cargotracker.Billing.Domain.Model.PaymentStatus (PaymentStatus (..))
import Cargotracker.Billing.Domain.Model.Value.Money (moneyAmount)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 10 12) (secondsToDiffTime 43200)

sampleCost :: ConfirmedCost
sampleCost =
  ConfirmedCost
    { ccAmount = 485000
    , ccCurrency = "JPY"
    , ccDiscountPercentage = 10
    , ccShipperId = "SHP-X1Y2Z3"
    }

-- | IORef ベースの fake InvoiceRepository
newFakeRepo :: [Invoice] -> IO (InvoiceRepository IO, IO [Invoice])
newFakeRepo initial = do
  ref <- newIORef initial
  let repo =
        InvoiceRepository
          { saveInvoice = \inv -> do
              modifyIORef' ref (inv :)
              pure (Right ())
          , findByInvoiceId = \iid -> do
              xs <- readIORef ref
              pure (lookupBy (\i -> unInvoiceId (invInvoiceId i) == iid) xs)
          , findInvoiceByBookingId = \bid -> do
              xs <- readIORef ref
              pure (lookupBy (\i -> unBillingBookingId (invBookingId i) == bid) xs)
          , updateInvoice = \inv -> do
              modifyIORef'
                ref
                (map (\i -> if invInvoiceId i == invInvoiceId inv then inv else i))
              pure (Right ())
          , findPendingWithDueDate = do
              xs <- readIORef ref
              pure [i | i <- xs, invPaymentStatus i == Pending, isJust (invDueDate i)]
          , findAllInvoices = readIORef ref
          }
  pure (repo, readIORef ref)
  where
    lookupBy p xs = case filter p xs of
      (x : _) -> Just x
      [] -> Nothing

deliveredBooking :: BookingCrossBcPort IO
deliveredBooking =
  BookingCrossBcPort
    { resolveBookingStatus = \_ -> pure (Just "DELIVERED")
    , markSettledByBookingId = \_ -> pure (Right ())
    }

-- | markSettledByBookingId の発火を spy 記録する Booking ポート
spyBooking :: IORef [Text] -> BookingCrossBcPort IO
spyBooking ref =
  BookingCrossBcPort
    { resolveBookingStatus = \_ -> pure (Just "DELIVERED")
    , markSettledByBookingId = \bid -> do
        modifyIORef' ref (bid :)
        pure (Right ())
    }

pricingWith :: Maybe ConfirmedCost -> PricingCrossBcPort IO
pricingWith mc = PricingCrossBcPort {resolveConfirmedCost = \_ -> pure mc}

-- | 通知発火を spy 記録する Notification ポート
spyNotif :: IORef [(Text, Text)] -> BillingNotificationPort IO
spyNotif ref =
  BillingNotificationPort
    { sendInvoiceNotification = \bid inv -> do
        modifyIORef' ref ((bid, inv) :)
        pure (Right ())
    , sendOverdueNotification = \bid inv -> do
        modifyIORef' ref ((bid, inv) :)
        pure (Right ())
    }

okGateway :: PaymentGateway IO
okGateway = PaymentGateway {verifyPayment = \_ -> pure (Right ())}

generateInput :: GenerateInvoiceInput
generateInput =
  GenerateInvoiceInput
    { inputInvoiceNumber = "INV-2026-0001"
    , inputBookingId = "BK-A1B2C3"
    , inputNow = sampleNow
    }

-- | 発行済 Invoice を用意するヘルパ
generateOne :: IO (InvoiceRepository IO, IO [Invoice])
generateOne = do
  (repo, dump) <- newFakeRepo []
  notifRef <- newIORef []
  r <-
    Generate.execute
      repo
      deliveredBooking
      (pricingWith (Just sampleCost))
      (spyNotif notifRef)
      generateInput
  case r of
    Left e -> error ("setup: " <> show e)
    Right _ -> pure (repo, dump)

spec :: Spec
spec = do
  describe "GenerateInvoiceCommand (US23 発行)" $ do
    it "Delivered 予約 + 確定料金で発行され、10% 割引が適用される" $ do
      (repo, _) <- newFakeRepo []
      notifRef <- newIORef []
      r <-
        Generate.execute
          repo
          deliveredBooking
          (pricingWith (Just sampleCost))
          (spyNotif notifRef)
          generateInput
      case r of
        Left e -> expectationFailure (show e)
        Right inv -> do
          moneyAmount (invFinalAmount inv) `shouldBe` 436500
          invPaymentStatus inv `shouldBe` Pending

    it "発行成功で精算書通知が 1 回発火する (受入基準 2)" $ do
      (repo, _) <- newFakeRepo []
      notifRef <- newIORef []
      _ <-
        Generate.execute
          repo
          deliveredBooking
          (pricingWith (Just sampleCost))
          (spyNotif notifRef)
          generateInput
      notifs <- readIORef notifRef
      notifs `shouldBe` [("BK-A1B2C3", "INV-2026-0001")]

    it "Delivered 以外は InvoiceNotAllowedBeforeDelivered" $ do
      (repo, _) <- newFakeRepo []
      notifRef <- newIORef []
      let confirmedBooking =
            deliveredBooking {resolveBookingStatus = \_ -> pure (Just "CONFIRMED")}
      r <-
        Generate.execute
          repo
          confirmedBooking
          (pricingWith (Just sampleCost))
          (spyNotif notifRef)
          generateInput
      void r `shouldBe` Left (InvoiceNotAllowedBeforeDelivered "BK-A1B2C3")

    it "予約不在は BookingNotFound" $ do
      (repo, _) <- newFakeRepo []
      notifRef <- newIORef []
      let noBooking = deliveredBooking {resolveBookingStatus = \_ -> pure Nothing}
      r <-
        Generate.execute
          repo
          noBooking
          (pricingWith (Just sampleCost))
          (spyNotif notifRef)
          generateInput
      void r `shouldBe` Left (BookingNotFound "BK-A1B2C3")

    it "二重発行は InvoiceAlreadyExists (1 予約 1 請求)" $ do
      (repo, _) <- generateOne
      notifRef <- newIORef []
      r <-
        Generate.execute
          repo
          deliveredBooking
          (pricingWith (Just sampleCost))
          (spyNotif notifRef)
          generateInput {inputInvoiceNumber = "INV-2026-0002"}
      void r `shouldBe` Left (InvoiceAlreadyExists "BK-A1B2C3")

    it "確定料金なしは PricingRuleNotFound" $ do
      (repo, _) <- newFakeRepo []
      notifRef <- newIORef []
      r <-
        Generate.execute
          repo
          deliveredBooking
          (pricingWith Nothing)
          (spyNotif notifRef)
          generateInput
      void r `shouldBe` Left (PricingRuleNotFound "BK-A1B2C3")

  describe "IssuePaymentCommand + ConfirmPaymentCommand (US23 入金)" $ do
    it "発行 → 入金確認で Confirmed になり Cargo.Settled が 1 回だけ発火 (受入基準 4)" $ do
      (repo, _) <- generateOne
      settledRef <- newIORef []
      _ <-
        Issue.execute
          repo
          IssuePaymentInput
            { inputInvoiceId = "INV-2026-0001"
            , inputDueDate = fromGregorian 2026 11 11
            , inputPaymentReference = "PAY-REF-1"
            }
      r <-
        Confirm.execute
          repo
          okGateway
          (spyBooking settledRef)
          ConfirmPaymentInput
            { inputInvoiceId = "INV-2026-0001"
            , inputPaymentReference = "PAY-REF-1"
            , inputNow = sampleNow
            }
      case r of
        Left e -> expectationFailure (show e)
        Right inv -> invPaymentStatus inv `shouldBe` Confirmed
      settled <- readIORef settledRef
      settled `shouldBe` ["BK-A1B2C3"]

    it "reference 不一致は PaymentReferenceMismatch で Settled 連動しない" $ do
      (repo, _) <- generateOne
      settledRef <- newIORef []
      _ <-
        Issue.execute
          repo
          (IssuePaymentInput "INV-2026-0001" (fromGregorian 2026 11 11) "PAY-REF-1")
      r <-
        Confirm.execute
          repo
          okGateway
          (spyBooking settledRef)
          (ConfirmPaymentInput "INV-2026-0001" "WRONG" sampleNow)
      void r `shouldBe` Left PaymentReferenceMismatch
      settled <- readIORef settledRef
      settled `shouldBe` []

    it "決済機関の検証失敗はそのまま返り Settled 連動しない" $ do
      (repo, _) <- generateOne
      settledRef <- newIORef []
      let ngGateway = PaymentGateway {verifyPayment = \_ -> pure (Left PaymentReferenceMismatch)}
      _ <-
        Issue.execute
          repo
          (IssuePaymentInput "INV-2026-0001" (fromGregorian 2026 11 11) "PAY-REF-1")
      r <-
        Confirm.execute
          repo
          ngGateway
          (spyBooking settledRef)
          (ConfirmPaymentInput "INV-2026-0001" "PAY-REF-1" sampleNow)
      void r `shouldBe` Left PaymentReferenceMismatch
      settled <- readIORef settledRef
      settled `shouldBe` []

    it "存在しない請求書は InvoiceNotFound" $ do
      (repo, _) <- newFakeRepo []
      settledRef <- newIORef []
      r <-
        Confirm.execute
          repo
          okGateway
          (spyBooking settledRef)
          (ConfirmPaymentInput "INV-NONE" "PAY-REF-1" sampleNow)
      void r `shouldBe` Left (InvoiceNotFound "INV-NONE")

  describe "OverdueCheckCommand (US23 期限超過)" $ do
    it "期限超過の Pending のみ Overdue に遷移し未払い通知が発火する (受入基準 5)" $ do
      (repo, dump) <- generateOne
      notifRef <- newIORef []
      _ <-
        Issue.execute
          repo
          (IssuePaymentInput "INV-2026-0001" (fromGregorian 2026 11 11) "PAY-REF-1")
      r <-
        Overdue.execute
          repo
          (spyNotif notifRef)
          (OverdueCheckInput (fromGregorian 2026 11 12))
      r `shouldBe` Right 1
      notifs <- readIORef notifRef
      notifs `shouldBe` [("BK-A1B2C3", "INV-2026-0001")]
      invoices <- dump
      map invPaymentStatus invoices `shouldBe` [Overdue]

    it "期限未到来は遷移せず通知も発火しない" $ do
      (repo, _) <- generateOne
      notifRef <- newIORef []
      _ <-
        Issue.execute
          repo
          (IssuePaymentInput "INV-2026-0001" (fromGregorian 2026 11 11) "PAY-REF-1")
      r <-
        Overdue.execute
          repo
          (spyNotif notifRef)
          (OverdueCheckInput (fromGregorian 2026 11 10))
      r `shouldBe` Right 0
      notifs <- readIORef notifRef
      notifs `shouldBe` []
