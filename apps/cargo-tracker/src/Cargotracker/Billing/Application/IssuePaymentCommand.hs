{- | 入金発行コマンド (US23, IT8)

確定済 Invoice に支払期日と reference_code を設定する
(ui_design.md 画面一覧「入金発行」)。Domain issuePayment (純粋) が
空 reference / 二重発行 / 確認済を検証する。
-}
module Cargotracker.Billing.Application.IssuePaymentCommand
  ( IssuePaymentInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (Day)

import Cargotracker.Billing.Application.Ports (InvoiceRepository (..))
import Cargotracker.Billing.Domain.Model.Invoice (Invoice, issuePayment)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data IssuePaymentInput = IssuePaymentInput
  { inputInvoiceId :: !Text
  , inputDueDate :: !Day
  , inputPaymentReference :: !Text
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  InvoiceRepository m ->
  IssuePaymentInput ->
  m (Either DomainError Invoice)
execute repo input = do
  mInvoice <- findByInvoiceId repo (inputInvoiceId input)
  case mInvoice of
    Nothing -> pure (Left (InvoiceNotFound (inputInvoiceId input)))
    Just invoice ->
      case issuePayment (inputDueDate input) (inputPaymentReference input) invoice of
        Left err -> pure (Left err)
        Right issued -> do
          updated <- updateInvoice repo issued
          case updated of
            Left err -> pure (Left err)
            Right () -> pure (Right issued)
