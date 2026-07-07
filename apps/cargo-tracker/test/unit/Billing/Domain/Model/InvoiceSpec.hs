{-# LANGUAGE OverloadedStrings #-}

{- | Invoice 集約のテスト (US23, IT8, Billing Context)

domain-model.md §6 のビジネスルールを網羅する。
hedgehog プロパティで割引の単調性と冪等性を検証する。
-}
module Billing.Domain.Model.InvoiceSpec (spec) where

import Control.Monad.IO.Class (liftIO)
import Data.Time (Day, UTCTime (..), fromGregorian, secondsToDiffTime)
import Hedgehog (Property, assert, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Billing.Domain.Model.Invoice
  ( Invoice (..),
    applyDiscount,
    confirmPayment,
    issuePayment,
    markOverdue,
    mkInvoice,
  )
import Cargotracker.Billing.Domain.Model.PaymentStatus (PaymentStatus (..), paymentStatusToText, textToPaymentStatus)
import Cargotracker.Billing.Domain.Model.Value.DiscountRate (mkDiscountRate, unDiscountRate)
import Cargotracker.Billing.Domain.Model.Value.Money (Money (..), mkMoney)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 10 12) (secondsToDiffTime 43200)

dueDay :: Day
dueDay = fromGregorian 2026 11 11

jpy :: Integer -> Money
jpy n = either (error . show) id (mkMoney n "JPY")

newInvoice :: Invoice
newInvoice =
  either (error . show) id $
    mkInvoice "INV-2026-0001" "BK-A1B2C3" "SHP-X1Y2Z3" (jpy 485000) sampleNow

runProp :: String -> Property -> Spec
runProp name p = it name $ do
  ok <- liftIO (check p)
  ok `shouldBe` True

-- | P-1: 0〜30% の任意の割引で final <= base (単調性)
prop_discountNeverIncreases :: Property
prop_discountNeverIncreases = property $ do
  p <- forAll (Gen.integral (Range.constant 0 30))
  case mkDiscountRate p of
    Left _ -> assert False
    Right r -> case applyDiscount r newInvoice of
      Left _ -> assert False
      Right inv -> assert (moneyAmount (invFinalAmount inv) <= moneyAmount (invBaseAmount inv))

-- | P-2: 割引後の金額は base × (100 - p) / 100 (切り捨て) に一致
prop_discountFormula :: Property
prop_discountFormula = property $ do
  p <- forAll (Gen.integral (Range.constant 0 30))
  base <- forAll (Gen.integral (Range.constant 0 10000000))
  let inv0 =
        either (error . show) id $
          mkInvoice "INV-1" "BK-1" "SHP-1" (jpy base) sampleNow
  case mkDiscountRate p >>= \r -> applyDiscount r inv0 of
    Left _ -> assert False
    Right inv -> moneyAmount (invFinalAmount inv) === base * (100 - p) `div` 100

-- | P-3: 範囲外の割引率は必ず InvalidDiscountRate
prop_invalidDiscountRate :: Property
prop_invalidDiscountRate = property $ do
  p <- forAll (Gen.choice [Gen.integral (Range.constant 31 1000), Gen.integral (Range.constant (-1000) (-1))])
  mkDiscountRate p === Left (InvalidDiscountRate p)

spec :: Spec
spec = do
  describe "mkInvoice (US23)" $ do
    it "正常系: Pending / 割引なし / finalAmount = baseAmount / version 0" $ do
      let inv = newInvoice
      invPaymentStatus inv `shouldBe` Pending
      unDiscountRate (invDiscountRate inv) `shouldBe` 0
      invFinalAmount inv `shouldBe` invBaseAmount inv
      invPaidAt inv `shouldBe` Nothing
      invDueDate inv `shouldBe` Nothing
      invPaymentReference inv `shouldBe` Nothing
      invVersion inv `shouldBe` 0
    it "空の請求書番号は InvalidInvoiceNumber" $
      mkInvoice "" "BK-1" "SHP-1" (jpy 100) sampleNow
        `shouldBe` Left (InvalidInvoiceNumber "")
    it "空の予約 ID は InvalidBookingId" $
      mkInvoice "INV-1" " " "SHP-1" (jpy 100) sampleNow
        `shouldBe` Left (InvalidBookingId " ")
    it "空の荷主 ID は InvalidShipperId" $
      mkInvoice "INV-1" "BK-1" "" (jpy 100) sampleNow
        `shouldBe` Left (InvalidShipperId "")

  describe "Money (US23)" $ do
    it "負の金額は InvalidCost" $ mkMoney (-1) "JPY" `shouldBe` Left (InvalidCost (-1))
    it "小文字通貨は InvalidCurrency" $ mkMoney 100 "jpy" `shouldBe` Left (InvalidCurrency "jpy")
    it "2 文字通貨は InvalidCurrency" $ mkMoney 100 "JP" `shouldBe` Left (InvalidCurrency "JP")
    it "0 円 JPY を受理する" $ mkMoney 0 "JPY" `shouldBe` Right (Money 0 "JPY")

  describe "applyDiscount (US23 / 法人割引)" $ do
    it "10% 割引: 485,000 → 436,500" $ do
      let Right r = mkDiscountRate 10
      case applyDiscount r newInvoice of
        Left e -> expectationFailure (show e)
        Right inv -> do
          moneyAmount (invFinalAmount inv) `shouldBe` 436500
          invVersion inv `shouldBe` 1
    it "30% (上限) を受理する" $
      fmap unDiscountRate (mkDiscountRate 30) `shouldBe` Right 30
    it "31% は InvalidDiscountRate" $
      mkDiscountRate 31 `shouldBe` Left (InvalidDiscountRate 31)
    it "入金発行後の割引変更は InvoicePaymentAlreadyIssued" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
          Right r = mkDiscountRate 10
      applyDiscount r issued `shouldBe` Left InvoicePaymentAlreadyIssued
    runProp "P-1: 割引後の金額は元金額以下" prop_discountNeverIncreases
    runProp "P-2: 割引計算式 base×(100-p)/100 切り捨てに一致" prop_discountFormula
    runProp "P-3: 0〜30 の範囲外は必ず InvalidDiscountRate" prop_invalidDiscountRate

  describe "issuePayment (US23 入金発行)" $ do
    it "正常系: dueDate + paymentReference が設定され Pending のまま" $ do
      case issuePayment dueDay "PAY-REF-8A3F2C" newInvoice of
        Left e -> expectationFailure (show e)
        Right inv -> do
          invDueDate inv `shouldBe` Just dueDay
          invPaymentReference inv `shouldBe` Just "PAY-REF-8A3F2C"
          invPaymentStatus inv `shouldBe` Pending
          invVersion inv `shouldBe` 1
    it "空の reference は InvalidPaymentReference" $
      issuePayment dueDay "  " newInvoice
        `shouldBe` Left (InvalidPaymentReference "  ")
    it "二重発行は InvoicePaymentAlreadyIssued" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
      issuePayment dueDay "PAY-REF-2" issued
        `shouldBe` Left InvoicePaymentAlreadyIssued

  describe "confirmPayment (US23 入金確認)" $ do
    it "reference 一致で Confirmed + paidAt 設定" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
      case confirmPayment "PAY-REF-1" sampleNow issued of
        Left e -> expectationFailure (show e)
        Right inv -> do
          invPaymentStatus inv `shouldBe` Confirmed
          invPaidAt inv `shouldBe` Just sampleNow
    it "reference 不一致は PaymentReferenceMismatch" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
      confirmPayment "WRONG" sampleNow issued
        `shouldBe` Left PaymentReferenceMismatch
    it "入金発行前 (reference 未設定) は PaymentReferenceMismatch" $
      confirmPayment "PAY-REF-1" sampleNow newInvoice
        `shouldBe` Left PaymentReferenceMismatch
    it "二重確認は InvoiceAlreadyConfirmed" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
          Right confirmed = confirmPayment "PAY-REF-1" sampleNow issued
      confirmPayment "PAY-REF-1" sampleNow confirmed
        `shouldBe` Left InvoiceAlreadyConfirmed
    it "Overdue からの入金確認も受理する (期限超過後の入金)" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
          Right overdue = markOverdue (fromGregorian 2026 11 12) issued
      case confirmPayment "PAY-REF-1" sampleNow overdue of
        Left e -> expectationFailure (show e)
        Right inv -> invPaymentStatus inv `shouldBe` Confirmed

  describe "markOverdue (US23 期限超過)" $ do
    it "dueDate 超過で Pending → Overdue" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
      case markOverdue (fromGregorian 2026 11 12) issued of
        Left e -> expectationFailure (show e)
        Right inv -> invPaymentStatus inv `shouldBe` Overdue
    it "dueDate ちょうどは未超過 (> で判定)" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
      case markOverdue dueDay issued of
        Left e -> expectationFailure (show e)
        Right inv -> invPaymentStatus inv `shouldBe` Pending
    it "Overdue の再判定は冪等 (version 不変)" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
          Right overdue = markOverdue (fromGregorian 2026 11 12) issued
      markOverdue (fromGregorian 2026 11 13) overdue `shouldBe` Right overdue
    it "Confirmed は InvoiceAlreadyConfirmed" $ do
      let Right issued = issuePayment dueDay "PAY-REF-1" newInvoice
          Right confirmed = confirmPayment "PAY-REF-1" sampleNow issued
      markOverdue (fromGregorian 2026 11 12) confirmed
        `shouldBe` Left InvoiceAlreadyConfirmed
    it "dueDate 未設定 (入金発行前) は変更なし" $
      markOverdue (fromGregorian 2026 11 12) newInvoice `shouldBe` Right newInvoice

  describe "PaymentStatus (DB CHECK 整合)" $ do
    it "全 4 状態が往復変換できる" $
      mapM (textToPaymentStatus . paymentStatusToText) [minBound .. maxBound]
        `shouldBe` Right [Pending, Confirmed, Overdue, Refunded]
    it "未知の文字列は InvalidPaymentStatus" $
      textToPaymentStatus "PAID" `shouldBe` Left (InvalidPaymentStatus "PAID")
