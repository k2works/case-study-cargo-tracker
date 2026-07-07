{- | 支払い状態 (US23, IT8, Billing BC)

domain-model.md §6: Payment は独立集約ではなく Invoice 集約内の
ステータス + 純粋関数で表現する (Scala 版 ADR 0019 と同方針)。
`paymentStatusToText` は invoice テーブルの CHECK 制約
(PENDING/CONFIRMED/OVERDUE/REFUNDED) と整合する。
-}
module Cargotracker.Billing.Domain.Model.PaymentStatus
  ( PaymentStatus (..),
    paymentStatusToText,
    textToPaymentStatus,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data PaymentStatus
  = -- | 未入金 (発行直後の初期状態)
    Pending
  | -- | 入金確認済 (Cargo.Settled 連動のトリガ)
    Confirmed
  | -- | 支払期限超過
    Overdue
  | -- | 返金済 (支払い確定後キャンセル)
    Refunded
  deriving stock (Eq, Show, Enum, Bounded)

paymentStatusToText :: PaymentStatus -> Text
paymentStatusToText Pending = "PENDING"
paymentStatusToText Confirmed = "CONFIRMED"
paymentStatusToText Overdue = "OVERDUE"
paymentStatusToText Refunded = "REFUNDED"

textToPaymentStatus :: Text -> Either DomainError PaymentStatus
textToPaymentStatus "PENDING" = Right Pending
textToPaymentStatus "CONFIRMED" = Right Confirmed
textToPaymentStatus "OVERDUE" = Right Overdue
textToPaymentStatus "REFUNDED" = Right Refunded
textToPaymentStatus other = Left (InvalidPaymentStatus other)
