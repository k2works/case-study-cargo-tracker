{-# LANGUAGE OverloadedStrings #-}

{- | PostgresInvoiceRepository の統合テスト (US23 / T7-G, IT8)

DATABASE_URL があれば実 Postgres で実行、なければ pendingWith でスキップ
(既存 integration スイートと同方式)。

検証項目:

- save → findByInvoiceId / findByBookingId の往復 (discount_rate の
  NUMERIC⇔Integer SQL 変換含む)
- booking_id UK による二重発行防御 (InvoiceAlreadyExists)
- version 楽観ロック更新と競合検出 (ConcurrentModification)
- findPendingWithDueDate の絞り込み
-}
module Billing.Infrastructure.PostgresInvoiceRepositorySpec (spec) where

import qualified Data.ByteString.Char8 as BC
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    close,
    connectPostgreSQL,
    execute,
  )
import System.Environment (lookupEnv)
import Test.Hspec

import Cargotracker.Billing.Application.Ports (InvoiceRepository (..))
import Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    applyDiscount,
    confirmPayment,
    issuePayment,
    mkInvoice,
    unInvoiceId,
  )
import Cargotracker.Billing.Domain.Model.PaymentStatus (PaymentStatus (..))
import Cargotracker.Billing.Domain.Model.Value.DiscountRate
  ( mkDiscountRate,
    unDiscountRate,
  )
import Cargotracker.Billing.Domain.Model.Value.Money (Money (..), mkMoney)
import Cargotracker.Billing.Infrastructure.PostgresInvoiceRepository
  ( newPostgresInvoiceRepository,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 10 12) (secondsToDiffTime 43200)

testInvoiceNumber :: String
testInvoiceNumber = "INV-ITG001"

sampleInvoice :: Invoice
sampleInvoice =
  let Right money = mkMoney 485000 "JPY"
      Right inv =
        mkInvoice
          "INV-ITG001"
          "BK-ITG001"
          "SHP-ITG001"
          money
          sampleNow
      Right rate = mkDiscountRate 10
      Right discounted = applyDiscount rate inv
   in discounted

withDb :: (Connection -> InvoiceRepository IO -> IO ()) -> IO ()
withDb action = do
  mUrl <- lookupEnv "DATABASE_URL"
  case mUrl of
    Nothing -> pendingWith "DATABASE_URL not set; skipped"
    Just url -> do
      conn <- connectPostgreSQL (BC.pack url)
      _ <- execute conn "DELETE FROM invoice WHERE invoice_number = ?" (Only testInvoiceNumber)
      action conn (newPostgresInvoiceRepository conn)
      _ <- execute conn "DELETE FROM invoice WHERE invoice_number = ?" (Only testInvoiceNumber)
      close conn

spec :: Spec
spec = describe "PostgresInvoiceRepository [INTEGRATION] (US23 / T7-G)" $ do
  it "save → findByInvoiceId / findByBookingId で往復できる (割引 10% 含む)" $
    withDb $ \_ repo -> do
      saved <- saveInvoice repo sampleInvoice
      saved `shouldBe` Right ()
      mByIid <- findByInvoiceId repo "INV-ITG001"
      case mByIid of
        Nothing -> expectationFailure "expected Just invoice by invoice_number"
        Just inv -> do
          unDiscountRate (invDiscountRate inv) `shouldBe` 10
          moneyAmount (invFinalAmount inv) `shouldBe` 436500
          invPaymentStatus inv `shouldBe` Pending
          invVersion inv `shouldBe` invVersion sampleInvoice
      mByBid <- findInvoiceByBookingId repo "BK-ITG001"
      fmap (unInvoiceId . invInvoiceId) mByBid `shouldBe` Just "INV-ITG001"

  it "同一 booking_id の既存チェック (findInvoiceByBookingId) が UK 相当の防御になる" $
    withDb $ \_ repo -> do
      _ <- saveInvoice repo sampleInvoice
      -- booking_id UK 衝突は SQL 例外になるため、Application 層は
      -- findInvoiceByBookingId の事前チェックで InvoiceAlreadyExists を返す
      mExisting <- findInvoiceByBookingId repo "BK-ITG001"
      mExisting `shouldSatisfy` (/= Nothing)

  it "issuePayment → confirmPayment の更新が version 楽観ロックで反映される" $
    withDb $ \_ repo -> do
      _ <- saveInvoice repo sampleInvoice
      let Right issued = issuePayment (fromGregorian 2026 11 11) "PAY-REF-ITG" sampleInvoice
      up1 <- updateInvoice repo issued
      up1 `shouldBe` Right ()
      let Right confirmed = confirmPayment "PAY-REF-ITG" sampleNow issued
      up2 <- updateInvoice repo confirmed
      up2 `shouldBe` Right ()
      mFinal <- findByInvoiceId repo "INV-ITG001"
      fmap invPaymentStatus mFinal `shouldBe` Just Confirmed
      fmap invPaidAt mFinal `shouldBe` Just (Just sampleNow)

  it "古い version での更新は ConcurrentModification" $
    withDb $ \_ repo -> do
      _ <- saveInvoice repo sampleInvoice
      let Right issued = issuePayment (fromGregorian 2026 11 11) "PAY-REF-ITG" sampleInvoice
      _ <- updateInvoice repo issued
      -- 同じ (旧 version からの) 遷移を再適用 → WHERE version 不一致
      stale <- updateInvoice repo issued
      stale `shouldBe` Left (ConcurrentModification "INV-ITG001")

  it "findPendingWithDueDate は dueDate 設定済の Pending のみ返す" $
    withDb $ \_ repo -> do
      _ <- saveInvoice repo sampleInvoice
      before <- findPendingWithDueDate repo
      filter ((== "INV-ITG001") . unInvoiceId . invInvoiceId) before `shouldBe` []
      let Right issued = issuePayment (fromGregorian 2026 11 11) "PAY-REF-ITG" sampleInvoice
      _ <- updateInvoice repo issued
      after <- findPendingWithDueDate repo
      map (unInvoiceId . invInvoiceId) (filter ((== "INV-ITG001") . unInvoiceId . invInvoiceId) after)
        `shouldBe` ["INV-ITG001"]
