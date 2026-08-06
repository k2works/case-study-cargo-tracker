{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 請求書 SSR 画面 API (US23, IT8)

ui_design.md 画面一覧の /billing/invoices 系 6 エンドポイント。
全エンドポイントに RoleGate (canManageBilling = Accountant/MasterAdmin) を
適用する (T7-A / ADR-0016 と同一パターン)。

- GET  /billing/invoices                          : 一覧 (status フィルタ)
- GET  /billing/invoices/new                      : 新規発行 (料金自動算出)
- POST /billing/invoices                          : 発行 (PRG 303)
- GET  /billing/invoices/:invoiceId               : 詳細
- POST /billing/invoices/:invoiceId/issue-payment : 入金発行 (PRG 303)
- POST /billing/invoices/:invoiceId/confirm-payment : 入金確認 (PRG 303)

resolveConfirmedCost は Pricing に予約単位の確定料金永続化がないため、
呼出側 (Main) がリクエスト非依存の port として合成して注入する
(iteration_plan-8 タスク 2.4 設計判断)。
-}
module Cargotracker.Billing.Interfaces.BillingPageApi
  ( BillingPageApi,
    billingPageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Char8 as BC
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (Day, getCurrentTime, utctDay)
import GHC.Generics (Generic)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm (..), parseUnique)

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
import Cargotracker.Billing.Application.Ports
  ( BillingNotificationPort,
    BookingCrossBcPort,
    ConfirmedCost (..),
    InvoiceRepository (..),
    PaymentGateway,
    PricingCrossBcPort (..),
  )
import Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    discountedAmount,
    unBillingBookingId,
    unBillingShipperId,
    unInvoiceId,
  )
import Cargotracker.Billing.Domain.Model.PaymentStatus (paymentStatusToText)
import Cargotracker.Billing.Domain.Model.Value.DiscountRate (mkDiscountRate, unDiscountRate)
import Cargotracker.Billing.Domain.Model.Value.Money (Money (..))
import Cargotracker.Billing.Views.InvoiceViews
  ( InvoiceRow (..),
    invoiceDetailPage,
    invoiceListPage,
    invoiceNewPage,
  )
import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository)
import Cargotracker.Shared.Auth.Domain.RolePolicy (canManageBilling)
import Cargotracker.Shared.Auth.Domain.User (Role, UserId)
import Cargotracker.Shared.Auth.Interfaces.RoleGate (requireRoleGate)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype GenerateInvoiceForm = GenerateInvoiceForm
  { formBookingId :: Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm GenerateInvoiceForm where
  fromForm f = GenerateInvoiceForm <$> parseUnique "bookingId" f

data IssuePaymentForm = IssuePaymentForm
  { formDueDate :: !Text
  , formPaymentReference :: !Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm IssuePaymentForm where
  fromForm f =
    IssuePaymentForm
      <$> parseUnique "dueDate" f
      <*> parseUnique "paymentReference" f

newtype ConfirmPaymentForm = ConfirmPaymentForm
  { formConfirmReference :: Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm ConfirmPaymentForm where
  fromForm f = ConfirmPaymentForm <$> parseUnique "paymentReference" f

type Redirect = Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)

type BillingPageApi =
  "billing"
    :> "invoices"
    :> Header "Cookie" Text
    :> ( QueryParam "status" Text :> QueryParam "flash" Text :> Get '[HTML] (Html ())
           :<|> "new" :> QueryParam "bookingId" Text :> QueryParam "flash" Text :> Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] GenerateInvoiceForm :> Redirect
           :<|> Capture "invoiceId" Text :> QueryParam "flash" Text :> Get '[HTML] (Html ())
           :<|> Capture "invoiceId" Text :> "issue-payment" :> ReqBody '[FormUrlEncoded] IssuePaymentForm :> Redirect
           :<|> Capture "invoiceId" Text :> "confirm-payment" :> ReqBody '[FormUrlEncoded] ConfirmPaymentForm :> Redirect
       )

billingPageApp ::
  InvoiceRepository IO ->
  BookingCrossBcPort IO ->
  PricingCrossBcPort IO ->
  BillingNotificationPort IO ->
  PaymentGateway IO ->
  -- | 請求書番号採番 (IO)
  IO Text ->
  SessionRepository IO ->
  (UserId -> IO [Role]) ->
  Application
billingPageApp repo bookingPort pricingPort notifPort gateway genInvoiceNumber sessionRepo lookupRoles =
  serve (Proxy :: Proxy BillingPageApi) $ \mCookie ->
    let gate = requireRoleGate sessionRepo lookupRoles getCurrentTime canManageBilling mCookie
     in handlerList repo gate
          :<|> handlerNew pricingPort gate
          :<|> handlerGenerate repo bookingPort pricingPort notifPort genInvoiceNumber gate
          :<|> handlerDetail repo gate
          :<|> handlerIssuePayment repo gate
          :<|> handlerConfirmPayment repo gateway bookingPort gate

--------------------------------------------------------------------------------
-- 表示ヘルパ
--------------------------------------------------------------------------------

formatMoney :: Money -> Text
formatMoney m = T.pack (show (moneyAmount m)) <> " " <> moneyCurrency m

invoiceToRow :: Invoice -> InvoiceRow
invoiceToRow inv =
  InvoiceRow
    { irInvoiceNumber = unInvoiceId (invInvoiceId inv)
    , irBookingId = unBillingBookingId (invBookingId inv)
    , irShipperId = unBillingShipperId (invShipperId inv)
    , irBaseAmount = formatMoney (invBaseAmount inv)
    , irDiscountRate = T.pack (show (unDiscountRate (invDiscountRate inv))) <> "%"
    , irFinalAmount = formatMoney (invFinalAmount inv)
    , irStatus = paymentStatusToText (invPaymentStatus inv)
    , irDueDate = maybe "-" (T.pack . show) (invDueDate inv)
    , irPaidAt = maybe "-" (T.pack . show) (invPaidAt inv)
    }

redirectTo :: Text -> Handler a
redirectTo loc =
  throwError
    err303
      { errHeaders = [("Location", BC.pack (T.unpack loc))]
      , errBody = ""
      }

errorFlash :: DomainError -> Text
errorFlash err = case err of
  InvoiceNotAllowedBeforeDelivered _ -> "not-delivered"
  BookingNotFound _ -> "not-found"
  InvoiceAlreadyExists _ -> "already-exists"
  InvoiceNotFound _ -> "not-found"
  PaymentReferenceMismatch -> "reference-mismatch"
  InvoiceAlreadyConfirmed -> "already-confirmed"
  _ -> "error"

--------------------------------------------------------------------------------
-- Handlers
--------------------------------------------------------------------------------

handlerList ::
  InvoiceRepository IO ->
  Handler a ->
  Maybe Text ->
  Maybe Text ->
  Handler (Html ())
handlerList repo gate mStatus mFlash = do
  _ <- gate
  invoices <- liftIO (findAllInvoices repo)
  let rows = map invoiceToRow invoices
      filtered = case mStatus of
        Just s | not (T.null s) -> filter ((== s) . irStatus) rows
        _ -> rows
  pure (invoiceListPage mFlash mStatus filtered)

handlerNew ::
  PricingCrossBcPort IO ->
  Handler a ->
  Maybe Text ->
  Maybe Text ->
  Handler (Html ())
handlerNew pricingPort gate mBookingId mFlash = do
  _ <- gate
  case mBookingId of
    Nothing -> pure (invoiceNewPage mFlash Nothing [])
    Just bid -> do
      mCost <- liftIO (resolveConfirmedCost pricingPort bid)
      let amounts = maybe [] costBreakdown mCost
      pure (invoiceNewPage mFlash (Just bid) amounts)
  where
    -- H-03 (IT8 レビュー): 割引後金額は Domain の discountedAmount を SSoT とし、
    -- 表示側で計算式を重複させない。
    costBreakdown c =
      [ ("基本料金", formatMoney (Money (ccAmount c) (ccCurrency c)))
      , ("法人割引", T.pack (show (ccDiscountPercentage c)) <> "%")
      ,
        ( "請求金額 (割引後)"
        , case mkDiscountRate (ccDiscountPercentage c) of
            Right rate -> formatMoney (discountedAmount rate (Money (ccAmount c) (ccCurrency c)))
            Left _ -> "算出不可"
        )
      ]

handlerGenerate ::
  InvoiceRepository IO ->
  BookingCrossBcPort IO ->
  PricingCrossBcPort IO ->
  BillingNotificationPort IO ->
  IO Text ->
  Handler a ->
  GenerateInvoiceForm ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerGenerate repo bookingPort pricingPort notifPort genInvoiceNumber gate form = do
  _ <- gate
  now <- liftIO getCurrentTime
  invoiceNumber <- liftIO genInvoiceNumber
  result <-
    liftIO
      ( Generate.execute
          repo
          bookingPort
          pricingPort
          notifPort
          GenerateInvoiceInput
            { inputInvoiceNumber = invoiceNumber
            , inputBookingId = formBookingId form
            , inputNow = now
            }
      )
  case result of
    Right _ -> pure (addHeader "/billing/invoices?flash=issued" NoContent)
    Left err ->
      redirectTo ("/billing/invoices/new?bookingId=" <> formBookingId form <> "&flash=" <> errorFlash err)

handlerDetail ::
  InvoiceRepository IO ->
  Handler a ->
  Text ->
  Maybe Text ->
  Handler (Html ())
handlerDetail repo gate invoiceId mFlash = do
  _ <- gate
  mInvoice <- liftIO (findByInvoiceId repo invoiceId)
  case mInvoice of
    Nothing -> redirectTo "/billing/invoices?flash=not-found"
    Just inv -> pure (invoiceDetailPage mFlash (invoiceToRow inv))

handlerIssuePayment ::
  InvoiceRepository IO ->
  Handler a ->
  Text ->
  IssuePaymentForm ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerIssuePayment repo gate invoiceId form = do
  _ <- gate
  case parseDay (formDueDate form) of
    Nothing -> redirectTo ("/billing/invoices/" <> invoiceId <> "?flash=bad-due-date")
    Just due -> do
      result <-
        liftIO
          ( Issue.execute
              repo
              IssuePaymentInput
                { Issue.inputInvoiceId = invoiceId
                , inputDueDate = due
                , Issue.inputPaymentReference = formPaymentReference form
                }
          )
      case result of
        Right _ -> pure (addHeader ("/billing/invoices/" <> invoiceId <> "?flash=payment-issued") NoContent)
        Left err -> redirectTo ("/billing/invoices/" <> invoiceId <> "?flash=" <> errorFlash err)

handlerConfirmPayment ::
  InvoiceRepository IO ->
  PaymentGateway IO ->
  BookingCrossBcPort IO ->
  Handler a ->
  Text ->
  ConfirmPaymentForm ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerConfirmPayment repo gateway bookingPort gate invoiceId form = do
  _ <- gate
  now <- liftIO getCurrentTime
  result <-
    liftIO
      ( Confirm.execute
          repo
          gateway
          bookingPort
          ConfirmPaymentInput
            { Confirm.inputInvoiceId = invoiceId
            , Confirm.inputPaymentReference = formConfirmReference form
            , Confirm.inputNow = now
            }
      )
  case result of
    Right _ -> pure (addHeader ("/billing/invoices/" <> invoiceId <> "?flash=confirmed") NoContent)
    Left err -> redirectTo ("/billing/invoices/" <> invoiceId <> "?flash=" <> errorFlash err)

-- | "YYYY-MM-DD" (input type=date) を Day に変換
parseDay :: Text -> Maybe Day
parseDay t = case reads (T.unpack t) of
  [(d, "")] -> Just d
  _ -> Nothing
