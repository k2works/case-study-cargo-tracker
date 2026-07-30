import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import type {
  InvoiceSummary,
  UnbilledBookingSummary,
} from '../../contexts/billing/application/queryservices/billing-query.service.js';
import { PAYMENT_STATUS_LABELS, type PaymentStatus } from '../../contexts/billing/domain/model/payment-status.js';

interface BillingIndexProps {
  user: AuthenticatedUser;
  unbilled: UnbilledBookingSummary[];
  invoices: InvoiceSummary[];
  success?: string;
}

const SHIPPER_TYPE_LABELS: Record<string, string> = {
  INDIVIDUAL: '個人',
  CORPORATE: '法人',
};

/** 請求書一覧画面（/billing/invoices）。未請求の予約と発行済み請求書を表示する（US21/US22） */
export function BillingIndex({ user, unbilled, invoices, success }: BillingIndexProps): ReactElement {
  return (
    <Layout title="請求書一覧" user={user} activePath="/billing/invoices">
      {success && (
        <div className="alert alert-success" role="alert" data-testid="billing-success">
          {success}
        </div>
      )}
      <h1 className="h3 mb-4" data-testid="billing-index-heading">
        請求管理
      </h1>

      <section className="mb-5">
        <h2 className="h5 mb-3">未請求の予約（引取済）</h2>
        {unbilled.length === 0 ? (
          <p className="text-muted" data-testid="no-unbilled">
            未請求の予約はありません。
          </p>
        ) : (
          <table className="table table-striped" data-testid="unbilled-table">
            <thead>
              <tr>
                <th>予約 ID</th>
                <th>荷主</th>
                <th>種別</th>
                <th>区間</th>
                <th>重量(kg)</th>
                <th>貨物種別</th>
                <th>状態</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {unbilled.map((row) => (
                <tr key={row.bookingId}>
                  <td>{row.bookingId}</td>
                  <td>{row.shipperName}</td>
                  <td>{SHIPPER_TYPE_LABELS[row.shipperType] ?? row.shipperType}</td>
                  <td>
                    {row.origin} → {row.destination}
                  </td>
                  <td>{row.weightKg}</td>
                  <td>{row.cargoType}</td>
                  <td>
                    <span className="badge bg-warning text-dark">未請求</span>
                  </td>
                  <td>
                    <a
                      href={`/billing/invoices/new?bookingId=${row.bookingId}`}
                      className="btn btn-primary btn-sm"
                      data-testid={`go-invoice-new-${row.bookingId}`}
                    >
                      料金を算出
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2 className="h5 mb-3">発行済み請求書</h2>
        {invoices.length === 0 ? (
          <p className="text-muted" data-testid="no-invoices">
            発行済みの請求書はありません。
          </p>
        ) : (
          <table className="table table-striped" data-testid="invoice-table">
            <thead>
              <tr>
                <th>請求番号</th>
                <th>予約 ID</th>
                <th>請求金額</th>
                <th>支払状態</th>
                <th>支払期限</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr key={invoice.invoiceNumber}>
                  <td>
                    <a
                      href={`/billing/invoices/${invoice.invoiceNumber}`}
                      data-testid={`go-invoice-detail-${invoice.invoiceNumber}`}
                    >
                      {invoice.invoiceNumber}
                    </a>
                  </td>
                  <td>{invoice.bookingId}</td>
                  <td>
                    {invoice.totalAmountValue.toLocaleString()} {invoice.totalAmountCurrency}
                  </td>
                  <td>{PAYMENT_STATUS_LABELS[invoice.paymentStatus as PaymentStatus] ?? invoice.paymentStatus}</td>
                  <td>{invoice.dueDate ? new Date(invoice.dueDate).toISOString().slice(0, 10) : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </Layout>
  );
}
