import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import type { InvoiceDetailView } from '../../contexts/billing/application/queryservices/billing-query.service.js';
import { PAYMENT_STATUS_LABELS, type PaymentStatus } from '../../contexts/billing/domain/model/payment-status.js';

interface BillingShowProps {
  user: AuthenticatedUser;
  invoice: InvoiceDetailView;
  csrfToken?: string;
  success?: string;
  error?: string;
}

const SHIPPER_TYPE_LABELS: Record<string, string> = {
  INDIVIDUAL: '個人',
  CORPORATE: '法人',
};

function formatDate(value: Date | null): string {
  return value ? new Date(value).toISOString().slice(0, 10) : '-';
}

/**
 * 請求書詳細画面（/billing/invoices/{invoiceNumber}・US23）。
 * 明細（基本料金・消費税・割引根拠・調整）・支払状態・支払期限・入金記録を表示し、
 * PENDING/OVERDUE の場合は入金確認（PRG）ボタンを表示する。
 */
export function BillingShow({ user, invoice, csrfToken, success, error }: BillingShowProps): ReactElement {
  const canConfirm = invoice.paymentStatus === 'PENDING' || invoice.paymentStatus === 'OVERDUE';
  return (
    <Layout title="請求書詳細" user={user} activePath="/billing/invoices" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="billing-success">
          {success}
        </div>
      )}
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="billing-error">
          {error}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="billing-show-heading">
        請求書詳細
      </h1>

      <section className="mb-4">
        <table className="table table-sm" data-testid="invoice-summary">
          <tbody>
            <tr>
              <th>請求番号</th>
              <td data-testid="invoice-number">{invoice.invoiceNumber}</td>
            </tr>
            <tr>
              <th>予約 ID</th>
              <td>{invoice.bookingId}</td>
            </tr>
            <tr>
              <th>荷主</th>
              <td>
                {invoice.shipperId}（{SHIPPER_TYPE_LABELS[invoice.shipperType] ?? invoice.shipperType}）
              </td>
            </tr>
            <tr>
              <th>請求金額</th>
              <td data-testid="invoice-total">
                {invoice.totalAmountValue.toLocaleString()} {invoice.totalAmountCurrency}
              </td>
            </tr>
            <tr>
              <th>支払状態</th>
              <td data-testid="invoice-status">
                {PAYMENT_STATUS_LABELS[invoice.paymentStatus as PaymentStatus] ?? invoice.paymentStatus}
              </td>
            </tr>
            <tr>
              <th>支払期限</th>
              <td>{formatDate(invoice.dueDate)}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section className="mb-4">
        <h2 className="h5">明細</h2>
        <table className="table table-sm" data-testid="invoice-line-items">
          <thead>
            <tr>
              <th>内容</th>
              <th className="text-end">金額</th>
            </tr>
          </thead>
          <tbody>
            {invoice.lineItems.map((item, index) => (
              <tr key={index}>
                <td>{item.description}</td>
                <td className="text-end">
                  {item.amountValue.toLocaleString()} {item.amountCurrency}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="mb-4">
        <h2 className="h5">入金記録</h2>
        {invoice.payments.length === 0 ? (
          <p className="text-muted" data-testid="no-payments">
            入金記録はありません。
          </p>
        ) : (
          <table className="table table-sm" data-testid="invoice-payments">
            <thead>
              <tr>
                <th>入金日時</th>
                <th>金額</th>
                <th>決済手段</th>
                <th>取引参照</th>
              </tr>
            </thead>
            <tbody>
              {invoice.payments.map((payment, index) => (
                <tr key={index}>
                  <td>{new Date(payment.paidAt).toISOString().slice(0, 10)}</td>
                  <td>
                    {payment.paidAmountValue.toLocaleString()} {payment.paidAmountCurrency}
                  </td>
                  <td>{payment.paymentMethod}</td>
                  <td>{payment.transactionReference ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <div className="d-flex gap-2">
        {canConfirm && (
          <form
            action={`/billing/invoices/${invoice.invoiceNumber}/confirm`}
            method="post"
            data-testid="confirm-payment-form"
          >
            {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
            <button type="submit" className="btn btn-primary" data-testid="confirm-payment-submit">
              入金を確認する
            </button>
          </form>
        )}
        <a href="/billing/invoices" className="btn btn-outline-secondary">
          一覧に戻る
        </a>
      </div>
    </Layout>
  );
}
