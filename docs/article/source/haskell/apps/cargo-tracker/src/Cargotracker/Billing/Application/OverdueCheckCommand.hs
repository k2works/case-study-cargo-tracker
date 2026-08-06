{- | 支払期限超過検知コマンド (US23, IT8)

業務フロー (受入基準 5):

1. Pending かつ dueDate 設定済の請求書を取得
2. Domain markOverdue (純粋、dueDate < today 判定) で Overdue に遷移
3. 遷移した請求書のみ updateInvoice + 未払い通知 (経理担当者宛)

通知失敗は他の請求書の処理を止めない (件数を返す)。
-}
module Cargotracker.Billing.Application.OverdueCheckCommand
  ( OverdueCheckInput (..),
    execute,
  ) where

import Data.Time (Day)

import Cargotracker.Billing.Application.Ports
  ( BillingNotificationPort (..),
    InvoiceRepository (..),
  )
import Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    markOverdue,
    unBillingBookingId,
    unInvoiceId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)

newtype OverdueCheckInput = OverdueCheckInput
  { inputToday :: Day
  }
  deriving stock (Eq, Show)

-- | Overdue に遷移させた件数を返す。
execute ::
  Monad m =>
  InvoiceRepository m ->
  BillingNotificationPort m ->
  OverdueCheckInput ->
  m (Either DomainError Int)
execute repo notifPort input = do
  candidates <- findPendingWithDueDate repo
  counts <- mapM step candidates
  pure (Right (sum counts))
  where
    step invoice =
      case markOverdue (inputToday input) invoice of
        Left _ -> pure (0 :: Int)
        Right updated
          | invPaymentStatus updated == invPaymentStatus invoice -> pure 0
          | otherwise -> do
              saved <- updateInvoice repo updated
              case saved of
                Left _ -> pure 0
                Right () -> do
                  _ <-
                    sendOverdueNotification
                      notifPort
                      (unBillingBookingId (invBookingId updated))
                      (unInvoiceId (invInvoiceId updated))
                  pure 1
