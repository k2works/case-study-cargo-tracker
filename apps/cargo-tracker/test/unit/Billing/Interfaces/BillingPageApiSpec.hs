{-# LANGUAGE OverloadedStrings #-}

{- | BillingPageApi の hspec-wai 統合テスト (US23, IT8)

- 認可マトリクス: Cookie なし 401 / Handler 403 / Accountant 200・303
- ハッピーパス: 発行 → 詳細 → 入金発行 → 入金確認 (PRG 303 + flash)
- 異常系: Delivered 前 / reference 不一致 の flash リダイレクト
-}
module Billing.Interfaces.BillingPageApiSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Maybe (isJust)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import qualified Network.HTTP.Types as HTTP
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

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
import Cargotracker.Billing.Interfaces.BillingPageApi (billingPageApp)
import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository (..))
import Cargotracker.Shared.Auth.Domain.Session (Session (..))
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken, mkSessionToken)
import Cargotracker.Shared.Auth.Domain.User (Role (..), UserId (..))
import Support.HspecWaiJa (bodyContainsText)

accountantToken :: T.Text
accountantToken = T.replicate 44 "a"

handlerToken :: T.Text
handlerToken = T.replicate 44 "b"

farFuture :: UTCTime
farFuture = UTCTime (fromGregorian 2099 1 1) (secondsToDiffTime 0)

mkTok :: T.Text -> SessionToken
mkTok = either (error . show) id . mkSessionToken

fakeSessionRepo :: SessionRepository IO
fakeSessionRepo =
  SessionRepository
    { saveSession = \_ -> pure (Right ())
    , findByToken = \tok ->
        pure $
          if tok == mkTok accountantToken
            then Just (Session (mkTok accountantToken) (UserId "acc-1") farFuture farFuture)
            else
              if tok == mkTok handlerToken
                then Just (Session (mkTok handlerToken) (UserId "handler-1") farFuture farFuture)
                else Nothing
    , deleteByToken = \_ -> pure (Right ())
    , touchLastUsed = \_ -> pure (Right ())
    }

lookupRolesFake :: UserId -> IO [Role]
lookupRolesFake (UserId "acc-1") = pure [Accountant]
lookupRolesFake (UserId "handler-1") = pure [Handler]
lookupRolesFake _ = pure []

sampleCost :: ConfirmedCost
sampleCost =
  ConfirmedCost
    { ccAmount = 485000
    , ccCurrency = "JPY"
    , ccDiscountPercentage = 10
    , ccShipperId = "SHP-X1Y2Z3"
    }

makeApp :: IO Network.Wai.Application
makeApp = do
  ref <- newIORef ([] :: [Invoice])
  let repo =
        InvoiceRepository
          { saveInvoice = \inv -> modifyIORef' ref (inv :) >> pure (Right ())
          , findByInvoiceId = \iid -> do
              xs <- readIORef ref
              pure (headMaybe [i | i <- xs, unInvoiceId (invInvoiceId i) == iid])
          , findInvoiceByBookingId = \bid -> do
              xs <- readIORef ref
              pure (headMaybe [i | i <- xs, unBillingBookingId (invBookingId i) == bid])
          , updateInvoice = \inv -> do
              modifyIORef'
                ref
                (map (\i -> if invInvoiceId i == invInvoiceId inv then inv else i))
              pure (Right ())
          , findPendingWithDueDate = do
              xs <- readIORef ref
              pure [i | i <- xs, isJust (invDueDate i)]
          , findAllInvoices = readIORef ref
          }
      bookingPort =
        BookingCrossBcPort
          { resolveBookingStatus = \bid ->
              pure (if bid == "BK-NODELIV" then Just "CONFIRMED" else Just "DELIVERED")
          , markSettledByBookingId = \_ -> pure (Right ())
          }
      pricingPort = PricingCrossBcPort {resolveConfirmedCost = \_ -> pure (Just sampleCost)}
      notifPort =
        BillingNotificationPort
          { sendInvoiceNotification = \_ _ -> pure (Right ())
          , sendOverdueNotification = \_ _ -> pure (Right ())
          }
      gateway = PaymentGateway {verifyPayment = \_ -> pure (Right ())}
  pure
    ( billingPageApp
        repo
        bookingPort
        pricingPort
        notifPort
        gateway
        (pure "INV-2026-0001")
        fakeSessionRepo
        lookupRolesFake
    )
  where
    headMaybe (x : _) = Just x
    headMaybe [] = Nothing

cookieOf :: T.Text -> HTTP.Header
cookieOf tok = ("Cookie", "cargo_session=" <> TE.encodeUtf8 tok)

formType :: HTTP.Header
formType = ("Content-Type", "application/x-www-form-urlencoded")

spec :: Spec
spec = describe "BillingPageApi (US23, IT8)" $ do
  describe "認可マトリクス (canManageBilling)" $
    with makeApp $ do
      it "Cookie なしの一覧は 401" $
        get "/billing/invoices" `shouldRespondWith` 401

      it "Handler Cookie の一覧は 403" $
        request "GET" "/billing/invoices" [cookieOf handlerToken] ""
          `shouldRespondWith` 403

      it "Accountant Cookie の一覧は 200" $
        request "GET" "/billing/invoices" [cookieOf accountantToken] ""
          `shouldRespondWith` 200 {matchBody = bodyContainsText "請求書一覧"}

      it "Handler Cookie の発行 POST は 403" $
        request
          "POST"
          "/billing/invoices"
          [cookieOf handlerToken, formType]
          "bookingId=BK-A1B2C3"
          `shouldRespondWith` 403

  describe "発行 → 入金発行 → 入金確認のハッピーパス" $
    with makeApp $ do
      it "発行は 303 + flash=issued" $
        request
          "POST"
          "/billing/invoices"
          [cookieOf accountantToken, formType]
          "bookingId=BK-A1B2C3"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/billing/invoices?flash=issued"]
            }

      it "発行後の詳細は 200 で請求番号を含む" $ do
        _ <-
          request
            "POST"
            "/billing/invoices"
            [cookieOf accountantToken, formType]
            "bookingId=BK-A1B2C3"
        request "GET" "/billing/invoices/INV-2026-0001" [cookieOf accountantToken] ""
          `shouldRespondWith` 200 {matchBody = bodyContainsText "INV-2026-0001"}

      it "入金発行 → 入金確認は 303 + flash=confirmed" $ do
        _ <-
          request
            "POST"
            "/billing/invoices"
            [cookieOf accountantToken, formType]
            "bookingId=BK-A1B2C3"
        _ <-
          request
            "POST"
            "/billing/invoices/INV-2026-0001/issue-payment"
            [cookieOf accountantToken, formType]
            "dueDate=2026-11-11&paymentReference=PAY-REF-1"
        request
          "POST"
          "/billing/invoices/INV-2026-0001/confirm-payment"
          [cookieOf accountantToken, formType]
          "paymentReference=PAY-REF-1"
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/billing/invoices/INV-2026-0001?flash=confirmed"]
            }

  describe "異常系 flash リダイレクト" $
    with makeApp $ do
      it "Delivered 前の予約への発行は flash=not-delivered" $
        request
          "POST"
          "/billing/invoices"
          [cookieOf accountantToken, formType]
          "bookingId=BK-NODELIV"
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/billing/invoices/new?bookingId=BK-NODELIV&flash=not-delivered"]
            }

      it "reference 不一致の入金確認は flash=reference-mismatch" $ do
        _ <-
          request
            "POST"
            "/billing/invoices"
            [cookieOf accountantToken, formType]
            "bookingId=BK-A1B2C3"
        _ <-
          request
            "POST"
            "/billing/invoices/INV-2026-0001/issue-payment"
            [cookieOf accountantToken, formType]
            "dueDate=2026-11-11&paymentReference=PAY-REF-1"
        request
          "POST"
          "/billing/invoices/INV-2026-0001/confirm-payment"
          [cookieOf accountantToken, formType]
          "paymentReference=WRONG"
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/billing/invoices/INV-2026-0001?flash=reference-mismatch"]
            }
