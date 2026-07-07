{-# LANGUAGE OverloadedStrings #-}

{- | Billing BC の PostgreSQL 実装 InvoiceRepository (US23, IT8)

invoice テーブルへの save / find / update を実装する。

- T-02 準拠: Tx 境界は Application / Interfaces 層で管理し、本実装は IO のみ
- 楽観ロック: updateInvoice は `WHERE invoice_number = ? AND version = ?` の
  比較更新とし、更新行数 0 を ConcurrentModification として扱う
  (data-model.md 設計判断)
- discount_rate は DB では NUMERIC(5,4) の比率 (0.1000 = 10%)、Domain では
  Integer 百分率 (10)。scientific 依存を避けるため変換は SQL 側で行う
  (書込 `?::numeric / 100`、読出 `round(discount_rate * 100)::bigint`)
-}
module Cargotracker.Billing.Infrastructure.PostgresInvoiceRepository
  ( newPostgresInvoiceRepository,
  ) where

import Data.Either (fromRight)
import Data.Text (Text)
import Data.Time (Day, UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    Query,
    execute,
    query,
  )

import Cargotracker.Billing.Application.Ports (InvoiceRepository (..))
import Cargotracker.Billing.Domain.Model.Invoice
  ( BillingBookingId (..),
    BillingShipperId (..),
    Invoice (..),
    InvoiceId (..),
  )
import Cargotracker.Billing.Domain.Model.PaymentStatus
  ( PaymentStatus (..),
    paymentStatusToText,
    textToPaymentStatus,
  )
import Cargotracker.Billing.Domain.Model.Value.DiscountRate
  ( mkDiscountRate,
    noDiscountRate,
    unDiscountRate,
  )
import Cargotracker.Billing.Domain.Model.Value.Money (Money (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newPostgresInvoiceRepository :: Connection -> InvoiceRepository IO
newPostgresInvoiceRepository conn =
  InvoiceRepository
    { saveInvoice = saveImpl conn
    , findByInvoiceId = findByInvoiceIdImpl conn
    , findInvoiceByBookingId = findByBookingIdImpl conn
    , updateInvoice = updateImpl conn
    , findPendingWithDueDate = findPendingWithDueDateImpl conn
    , findAllInvoices = findAllImpl conn
    }

--------------------------------------------------------------------------------
-- 行型と変換
--------------------------------------------------------------------------------

{- | invoice_number, booking_id, shipper_id, base_value, base_cur,
discount_percent (SQL 側で ×100 済), final_value, final_cur,
payment_status, issued_at, due_date, paid_at, payment_reference, version
-}
type InvoiceRow =
  ( Text
  , Text
  , Text
  , Integer
  , Text
  , Integer
  , Integer
  , Text
  , Text
  , Maybe UTCTime
  , Maybe Day
  , Maybe UTCTime
  , Maybe Text
  , Int
  )

rowToInvoice :: InvoiceRow -> Invoice
rowToInvoice (inum, bid, sid, baseV, baseC, discP, finV, finC, status, issued, due, paid, ref, ver) =
  Invoice
    { invInvoiceId = InvoiceId inum
    , invBookingId = BillingBookingId bid
    , invShipperId = BillingShipperId sid
    , invBaseAmount = Money baseV baseC
    , invDiscountRate = fromRight noDiscountRate (mkDiscountRate discP)
    , invFinalAmount = Money finV finC
    , invPaymentStatus =
        fromRight Pending (textToPaymentStatus status)
    , invIssuedAt = issued
    , invPaidAt = paid
    , invDueDate = due
    , invPaymentReference = ref
    , invVersion = ver
    }

selectColumns :: Query
selectColumns =
  "SELECT invoice_number, booking_id, shipper_id, \
  \       base_amount_value, base_amount_currency, \
  \       round(discount_rate * 100)::bigint, \
  \       final_amount_value, final_amount_currency, \
  \       payment_status, issued_at, due_date, paid_at, payment_reference, version \
  \ FROM invoice "

--------------------------------------------------------------------------------
-- 実装
--------------------------------------------------------------------------------

saveImpl :: Connection -> Invoice -> IO (Either DomainError ())
saveImpl conn inv = do
  n <-
    execute
      conn
      "INSERT INTO invoice \
      \ (invoice_number, booking_id, shipper_id, \
      \  base_amount_value, base_amount_currency, discount_rate, \
      \  final_amount_value, final_amount_currency, \
      \  payment_status, issued_at, due_date, paid_at, payment_reference, version) \
      \ VALUES (?, ?, ?, ?, ?, ?::numeric / 100, ?, ?, ?, ?, ?, ?, ?, ?)"
      ( unInvoiceId (invInvoiceId inv)
      , unBillingBookingId (invBookingId inv)
      , unBillingShipperId (invShipperId inv)
      , moneyAmount (invBaseAmount inv)
      , moneyCurrency (invBaseAmount inv)
      , unDiscountRate (invDiscountRate inv)
      , moneyAmount (invFinalAmount inv)
      , moneyCurrency (invFinalAmount inv)
      , paymentStatusToText (invPaymentStatus inv)
      , invIssuedAt inv
      , invDueDate inv
      , invPaidAt inv
      , invPaymentReference inv
      , invVersion inv
      )
  if n == 1
    then pure (Right ())
    else pure (Left (InvoiceAlreadyExists (unBillingBookingId (invBookingId inv))))

findByInvoiceIdImpl :: Connection -> Text -> IO (Maybe Invoice)
findByInvoiceIdImpl conn iid = do
  rows <-
    query conn (selectColumns <> " WHERE invoice_number = ?") (Only iid) ::
      IO [InvoiceRow]
  pure (fmap rowToInvoice (headMaybe rows))

findByBookingIdImpl :: Connection -> Text -> IO (Maybe Invoice)
findByBookingIdImpl conn bid = do
  rows <-
    query conn (selectColumns <> " WHERE booking_id = ?") (Only bid) ::
      IO [InvoiceRow]
  pure (fmap rowToInvoice (headMaybe rows))

findPendingWithDueDateImpl :: Connection -> IO [Invoice]
findPendingWithDueDateImpl conn = do
  rows <-
    query
      conn
      (selectColumns <> " WHERE payment_status = 'PENDING' AND due_date IS NOT NULL ORDER BY due_date ASC")
      () ::
      IO [InvoiceRow]
  pure (map rowToInvoice rows)

findAllImpl :: Connection -> IO [Invoice]
findAllImpl conn = do
  rows <-
    query
      conn
      (selectColumns <> " ORDER BY issued_at DESC NULLS LAST, invoice_number DESC")
      () ::
      IO [InvoiceRow]
  pure (map rowToInvoice rows)

{- | version 比較更新 (楽観ロック)。Domain 関数は遷移時に version を +1 して
返すため、WHERE 句は「更新前の version = 新 version - 1」で照合する。
-}
updateImpl :: Connection -> Invoice -> IO (Either DomainError ())
updateImpl conn inv = do
  n <-
    execute
      conn
      "UPDATE invoice \
      \ SET discount_rate = ?::numeric / 100, \
      \     final_amount_value = ?, final_amount_currency = ?, \
      \     payment_status = ?, due_date = ?, paid_at = ?, \
      \     payment_reference = ?, version = ?, updated_at = NOW() \
      \ WHERE invoice_number = ? AND version = ?"
      ( unDiscountRate (invDiscountRate inv)
      , moneyAmount (invFinalAmount inv)
      , moneyCurrency (invFinalAmount inv)
      , paymentStatusToText (invPaymentStatus inv)
      , invDueDate inv
      , invPaidAt inv
      , invPaymentReference inv
      , invVersion inv
      , unInvoiceId (invInvoiceId inv)
      , invVersion inv - 1
      )
  if n == 1
    then pure (Right ())
    else pure (Left (ConcurrentModification (unInvoiceId (invInvoiceId inv))))

--------------------------------------------------------------------------------
-- 小物
--------------------------------------------------------------------------------

headMaybe :: [a] -> Maybe a
headMaybe (x : _) = Just x
headMaybe [] = Nothing
