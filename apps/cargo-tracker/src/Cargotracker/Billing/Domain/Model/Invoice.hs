{- | Invoice 集約 (US23, IT8, Billing Context)

domain-model.md §6 に準拠。Payment は独立集約とせず、Invoice 集約内の
ステータス + 純粋関数 (`applyDiscount` / `issuePayment` / `confirmPayment` /
`markOverdue`) で表現する (Scala 版 ADR 0019 と同方針)。

ビジネスルール (domain-model.md §6):

1. Invoice は BookingStatus == Delivered 後にのみ発行可能 (Application 層で検証)
2. 法人荷主には最大 30% の割引を適用 (DiscountRate VO で強制)
3. 支払期限超過時、PaymentStatus を Overdue に更新
4. 金額計算は Money (Integer 最小通貨単位) 上で行い、端数は切り捨て

T-03 準拠: 本モジュールは IO を含まない。現在時刻は引数で受け取る。
-}
module Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    InvoiceId (..),
    BillingBookingId (..),
    BillingShipperId (..),
    mkInvoice,
    applyDiscount,
    issuePayment,
    confirmPayment,
    markOverdue,
  ) where

import Data.Maybe (isJust)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (Day, UTCTime)

import Cargotracker.Billing.Domain.Model.PaymentStatus (PaymentStatus (..))
import Cargotracker.Billing.Domain.Model.Value.DiscountRate
  ( DiscountRate,
    noDiscountRate,
    unDiscountRate,
  )
import Cargotracker.Billing.Domain.Model.Value.Money (Money (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype InvoiceId = InvoiceId {unInvoiceId :: Text}
  deriving stock (Eq, Show)

-- | Cargo との関連 (Cross-BC 参照は Text ベース、ADR-0004 Rule 4)
newtype BillingBookingId = BillingBookingId {unBillingBookingId :: Text}
  deriving stock (Eq, Show)

newtype BillingShipperId = BillingShipperId {unBillingShipperId :: Text}
  deriving stock (Eq, Show)

data Invoice = Invoice
  { invInvoiceId :: !InvoiceId
  , invBookingId :: !BillingBookingId
  , invShipperId :: !BillingShipperId
  , invBaseAmount :: !Money
  , invDiscountRate :: !DiscountRate
  , invFinalAmount :: !Money
  , invPaymentStatus :: !PaymentStatus
  , invIssuedAt :: !(Maybe UTCTime)
  , invPaidAt :: !(Maybe UTCTime)
  , invDueDate :: !(Maybe Day)
  , invPaymentReference :: !(Maybe Text)
  , invVersion :: !Int
  }
  deriving stock (Eq, Show)

{- | 新規請求書を構築する。初期状態は Pending、割引なし、
finalAmount = baseAmount。発行時刻は `issuedAt` として受け取る。
-}
mkInvoice ::
  Text ->
  Text ->
  Text ->
  Money ->
  UTCTime ->
  Either DomainError Invoice
mkInvoice invoiceId bookingId shipperId baseAmount issuedAt
  | T.null (T.strip invoiceId) = Left (InvalidInvoiceNumber invoiceId)
  | T.null (T.strip bookingId) = Left (InvalidBookingId bookingId)
  | T.null (T.strip shipperId) = Left (InvalidShipperId shipperId)
  | otherwise =
      Right
        Invoice
          { invInvoiceId = InvoiceId (T.strip invoiceId)
          , invBookingId = BillingBookingId (T.strip bookingId)
          , invShipperId = BillingShipperId (T.strip shipperId)
          , invBaseAmount = baseAmount
          , invDiscountRate = noDiscountRate
          , invFinalAmount = baseAmount
          , invPaymentStatus = Pending
          , invIssuedAt = Just issuedAt
          , invPaidAt = Nothing
          , invDueDate = Nothing
          , invPaymentReference = Nothing
          , invVersion = 0
          }

{- | 割引を適用して finalAmount を再計算する。
finalAmount = baseAmount × (100 - rate) / 100 (端数切り捨て)。
入金発行後 (paymentReference 設定後) の割引変更は不可。
-}
applyDiscount :: DiscountRate -> Invoice -> Either DomainError Invoice
applyDiscount rate inv
  | invPaymentStatus inv == Confirmed = Left InvoiceAlreadyConfirmed
  | isJust (invPaymentReference inv) = Left InvoicePaymentAlreadyIssued
  | otherwise =
      let base = invBaseAmount inv
          discounted = moneyAmount base * (100 - unDiscountRate rate) `div` 100
       in Right
            inv
              { invDiscountRate = rate
              , invFinalAmount = base {moneyAmount = discounted}
              , invVersion = invVersion inv + 1
              }

{- | 入金発行: 支払期日と reference_code を設定する (Pending のまま)。
reference_code が空、または既発行 (二重発行) はエラー。
-}
issuePayment :: Day -> Text -> Invoice -> Either DomainError Invoice
issuePayment dueDate reference inv
  | invPaymentStatus inv == Confirmed = Left InvoiceAlreadyConfirmed
  | T.null (T.strip reference) = Left (InvalidPaymentReference reference)
  | isJust (invPaymentReference inv) = Left InvoicePaymentAlreadyIssued
  | otherwise =
      Right
        inv
          { invDueDate = Just dueDate
          , invPaymentReference = Just (T.strip reference)
          , invVersion = invVersion inv + 1
          }

{- | 入金確認: reference_code を照合し、一致すれば Confirmed + paidAt を設定。
期限超過 (Overdue) 後の入金も確認できる。二重確認は不可。
-}
confirmPayment :: Text -> UTCTime -> Invoice -> Either DomainError Invoice
confirmPayment reference now inv =
  case invPaymentStatus inv of
    Confirmed -> Left InvoiceAlreadyConfirmed
    Refunded -> Left InvoiceAlreadyConfirmed
    _ ->
      case invPaymentReference inv of
        Nothing -> Left PaymentReferenceMismatch
        Just registered
          | registered /= reference -> Left PaymentReferenceMismatch
          | otherwise ->
              Right
                inv
                  { invPaymentStatus = Confirmed
                  , invPaidAt = Just now
                  , invVersion = invVersion inv + 1
                  }

{- | 支払期限超過の判定と遷移。Pending かつ dueDate < today なら Overdue。
未到来なら変更なし (Right のまま返す、冪等)。Overdue の再判定も冪等。
Confirmed / Refunded は変更不可。
-}
markOverdue :: Day -> Invoice -> Either DomainError Invoice
markOverdue today inv =
  case invPaymentStatus inv of
    Confirmed -> Left InvoiceAlreadyConfirmed
    Refunded -> Left InvoiceAlreadyConfirmed
    Overdue -> Right inv
    Pending ->
      case invDueDate inv of
        Just due
          | today > due ->
              Right
                inv
                  { invPaymentStatus = Overdue
                  , invVersion = invVersion inv + 1
                  }
        _ -> Right inv
