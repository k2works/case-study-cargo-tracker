{- | Billing Context 出力ポート (US23, IT8)

T-02 準拠: Repository は IO のみ、Tx 境界は Application 側で管理する。
Cross-BC 参照 (Booking / Pricing / Notification) は Text DTO のみを
受け渡す (ADR-0004 Rule 4 / ADR-0012 準拠)。
-}
module Cargotracker.Billing.Application.Ports
  ( InvoiceRepository (..),
    PaymentGateway (..),
    BookingCrossBcPort (..),
    PricingCrossBcPort (..),
    BillingNotificationPort (..),
    ConfirmedCost (..),
  ) where

import Data.Text (Text)

import Cargotracker.Billing.Domain.Model.Invoice (Invoice)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data InvoiceRepository m = InvoiceRepository
  { saveInvoice :: Invoice -> m (Either DomainError ())
  -- ^ 新規請求書を永続化 (booking_id UK により 1 予約 1 請求を DB でも防御)
  , findByInvoiceId :: Text -> m (Maybe Invoice)
  , findInvoiceByBookingId :: Text -> m (Maybe Invoice)
  -- ^ 二重発行チェック用 (InvoiceAlreadyExists)
  , updateInvoice :: Invoice -> m (Either DomainError ())
  -- ^ version 楽観ロック比較更新 (更新行数 0 は ConcurrentModification)
  , findPendingWithDueDate :: m [Invoice]
  -- ^ OverdueCheckCommand 用: Pending かつ dueDate 設定済の請求書一覧
  , findAllInvoices :: m [Invoice]
  -- ^ 一覧画面用 (IT8 暫定ページング無し、issued_at DESC)
  }

{- | 決済機関 IF (US23 受入基準 3)。IT8 では fake 実装で受入基準を満たし、
実連携は Release 2.0 スコープ外とする (iteration_plan-8 リスク対策)。
-}
newtype PaymentGateway m = PaymentGateway
  { verifyPayment :: Text -> m (Either DomainError ())
  -- ^ reference_code の入金実在確認
  }

-- | Booking BC への Cross-BC ポート (Text DTO のみ)
data BookingCrossBcPort m = BookingCrossBcPort
  { resolveBookingStatus :: Text -> m (Maybe Text)
  -- ^ booking_id → BookingStatus の Text 表現 (例: "DELIVERED")。不在は Nothing
  , markSettledByBookingId :: Text -> m (Either DomainError ())
  -- ^ 入金確認後の Cargo.Settled 連動 (canTransitionTo Delivered Settled)
  }

-- | Pricing BC への Cross-BC ポート (確定料金の解決)
newtype PricingCrossBcPort m = PricingCrossBcPort
  { resolveConfirmedCost :: Text -> m (Maybe ConfirmedCost)
  -- ^ booking_id → 確定済み輸送料金。未確定は Nothing
  }

-- | Pricing BC から受け取る確定料金の Text/数値 DTO
data ConfirmedCost = ConfirmedCost
  { ccAmount :: !Integer
  -- ^ 最小通貨単位
  , ccCurrency :: !Text
  , ccDiscountPercentage :: !Integer
  -- ^ 法人割引率 (0-30、ADR-0015 contract_rank 由来)
  , ccShipperId :: !Text
  }
  deriving stock (Eq, Show)

-- | Notification BC への Cross-BC ポート (精算書通知 / 未払い通知)
data BillingNotificationPort m = BillingNotificationPort
  { sendInvoiceNotification :: Text -> Text -> m (Either DomainError ())
  -- ^ bookingId → invoiceNumber の精算書発行通知 (荷主宛)
  , sendOverdueNotification :: Text -> Text -> m (Either DomainError ())
  -- ^ bookingId → invoiceNumber の未払い通知 (経理担当者宛)
  }
