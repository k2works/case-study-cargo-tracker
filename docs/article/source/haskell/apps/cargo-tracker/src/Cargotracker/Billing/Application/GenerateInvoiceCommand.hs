{- | 精算書発行コマンド (US23, IT8)

業務フロー (受入基準 1・2):

1. Booking BC で予約状態を解決し、Delivered 以外は発行不可
   (domain-model.md §6 ビジネスルール 1)
2. 既存請求書があれば InvoiceAlreadyExists (1 予約 1 請求)
3. Pricing BC から確定料金 + 法人割引率 (ADR-0015) を解決
4. mkInvoice + applyDiscount (純粋) → InvoiceRepository.saveInvoice
5. Notification BC へ精算書発行通知 (Tx 外の副作用、ADR-0012。
   通知失敗は発行自体を失敗させない)

T-03 準拠: 請求書番号の採番 IO は呼び出し側 (Interfaces) で行い、
inputInvoiceNumber として受け取る。
-}
module Cargotracker.Billing.Application.GenerateInvoiceCommand
  ( GenerateInvoiceInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Billing.Application.Ports
  ( BillingNotificationPort (..),
    BookingCrossBcPort (..),
    ConfirmedCost (..),
    InvoiceRepository (..),
    PricingCrossBcPort (..),
  )
import Cargotracker.Billing.Domain.Model.Invoice (Invoice, applyDiscount, mkInvoice)
import Cargotracker.Billing.Domain.Model.Value.DiscountRate (mkDiscountRate)
import Cargotracker.Billing.Domain.Model.Value.Money (mkMoney)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data GenerateInvoiceInput = GenerateInvoiceInput
  { inputInvoiceNumber :: !Text
  -- ^ 採番済みの請求書番号 (呼び出し側で IO)
  , inputBookingId :: !Text
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  InvoiceRepository m ->
  BookingCrossBcPort m ->
  PricingCrossBcPort m ->
  BillingNotificationPort m ->
  GenerateInvoiceInput ->
  m (Either DomainError Invoice)
execute repo bookingPort pricingPort notifPort input = do
  mStatus <- resolveBookingStatus bookingPort (inputBookingId input)
  case mStatus of
    Nothing -> pure (Left (BookingNotFound (inputBookingId input)))
    Just status
      | status /= "DELIVERED" ->
          pure (Left (InvoiceNotAllowedBeforeDelivered (inputBookingId input)))
      | otherwise -> do
          mExisting <- findInvoiceByBookingId repo (inputBookingId input)
          case mExisting of
            Just _ -> pure (Left (InvoiceAlreadyExists (inputBookingId input)))
            Nothing -> do
              mCost <- resolveConfirmedCost pricingPort (inputBookingId input)
              case mCost of
                Nothing -> pure (Left (PricingRuleNotFound (inputBookingId input)))
                Just cost -> buildAndSave cost
  where
    buildAndSave cost =
      case buildInvoice cost of
        Left err -> pure (Left err)
        Right invoice -> do
          saved <- saveInvoice repo invoice
          case saved of
            Left err -> pure (Left err)
            Right () -> do
              -- 通知は副作用 (失敗しても発行は成立、ADR-0012 副作用外出し)
              _ <-
                sendInvoiceNotification
                  notifPort
                  (inputBookingId input)
                  (inputInvoiceNumber input)
              pure (Right invoice)

    buildInvoice cost = do
      base <- mkMoney (ccAmount cost) (ccCurrency cost)
      inv <-
        mkInvoice
          (inputInvoiceNumber input)
          (inputBookingId input)
          (ccShipperId cost)
          base
          (inputNow input)
      rate <- mkDiscountRate (ccDiscountPercentage cost)
      applyDiscount rate inv
