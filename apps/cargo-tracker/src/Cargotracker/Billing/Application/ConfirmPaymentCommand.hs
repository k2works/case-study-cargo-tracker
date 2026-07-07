{- | 入金確認コマンド (US23, IT8)

業務フロー (受入基準 3・4):

1. InvoiceRepository.findByInvoiceId で請求書を取得 (不在は InvoiceNotFound)
2. PaymentGateway.verifyPayment で決済機関の入金実在確認 (fake 可)
3. Domain confirmPayment (reference 照合 → Confirmed + paidAt、純粋)
4. updateInvoice (version 楽観ロック)
5. BookingCrossBcPort.markSettledByBookingId で Cargo.Settled 連動
   (受入基準 4: 「予約状態も精算済になる」)

invoice 更新と Cargo.Settled は不可分 (iteration_plan-8 Tx 境界)。
Tx 境界は Interfaces 層が TxRunner で囲む (T-01)。
-}
module Cargotracker.Billing.Application.ConfirmPaymentCommand
  ( ConfirmPaymentInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Billing.Application.Ports
  ( BookingCrossBcPort (..),
    InvoiceRepository (..),
    PaymentGateway (..),
  )
import Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    confirmPayment,
    unBillingBookingId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data ConfirmPaymentInput = ConfirmPaymentInput
  { inputInvoiceId :: !Text
  , inputPaymentReference :: !Text
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  InvoiceRepository m ->
  PaymentGateway m ->
  BookingCrossBcPort m ->
  ConfirmPaymentInput ->
  m (Either DomainError Invoice)
execute repo gateway bookingPort input = do
  mInvoice <- findByInvoiceId repo (inputInvoiceId input)
  case mInvoice of
    Nothing -> pure (Left (InvoiceNotFound (inputInvoiceId input)))
    Just invoice -> do
      verified <- verifyPayment gateway (inputPaymentReference input)
      case verified of
        Left err -> pure (Left err)
        Right () ->
          case confirmPayment (inputPaymentReference input) (inputNow input) invoice of
            Left err -> pure (Left err)
            Right confirmed -> do
              updated <- updateInvoice repo confirmed
              case updated of
                Left err -> pure (Left err)
                Right () -> do
                  settled <-
                    markSettledByBookingId
                      bookingPort
                      (unBillingBookingId (invBookingId confirmed))
                  case settled of
                    Left err -> pure (Left err)
                    Right () -> pure (Right confirmed)
