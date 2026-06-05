import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  type BillingStatus,
  type Invoice,
  billingStatusLabel,
  fetchInvoicesPage,
} from '../api/billingApi';

/**
 * S22 請求一覧画面（US23、IT7 T4.7、ROLE_ACCOUNTANT）。
 *
 * <p>iteration_plan-7 §UI 設計 S22 / ui_design.md L112-115 準拠。billing_status フィルタ
 * （PENDING / CALCULATED / INVOICED / PAID / OVERDUE / CANCELLED）に対応。
 * 各行クリックで S23 請求詳細画面へ遷移する。</p>
 */
const STATUSES: BillingStatus[] = [
  'PENDING',
  'CALCULATED',
  'INVOICED',
  'PAID',
  'OVERDUE',
  'CANCELLED',
];

const PAGE_SIZE = 20;

export default function InvoiceListPage() {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<BillingStatus | ''>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const promise = fetchInvoicesPage(page, PAGE_SIZE, status || undefined);
    promise
      .then((res) => {
        if (!cancelled) {
          setInvoices(res.items);
          setTotalCount(res.totalCount);
          setError(null);
          setLoading(false);
        }
      })
      .catch((e: Error) => {
        if (!cancelled) {
          setInvoices([]);
          setError(e.message);
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [page, status]);

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <h1 className="text-2xl font-bold mb-4">請求一覧</h1>

      <div className="mb-4 flex items-center gap-3">
        <label htmlFor="status-filter" className="text-sm text-gray-700">
          状態フィルタ
        </label>
        <select
          id="status-filter"
          value={status}
          onChange={(e) => {
            setPage(0);
            setStatus(e.target.value as BillingStatus | '');
          }}
          className="rounded border border-gray-300 px-2 py-1 text-sm"
        >
          <option value="">すべて</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {billingStatusLabel(s)}
            </option>
          ))}
        </select>
        <span className="ml-auto text-sm text-gray-500">合計 {totalCount} 件</span>
      </div>

      {error && (
        <p className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
      )}

      {loading ? (
        <p className="text-gray-500">読み込み中…</p>
      ) : invoices.length === 0 ? (
        <p className="text-gray-500">該当する請求書がありません</p>
      ) : (
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="border-b border-gray-300 text-left">
              <th className="py-2 px-2">請求書 ID</th>
              <th className="py-2 px-2">予約 ID</th>
              <th className="py-2 px-2">荷主 ID</th>
              <th className="py-2 px-2 text-right">合計金額</th>
              <th className="py-2 px-2">状態</th>
              <th className="py-2 px-2">支払期限</th>
            </tr>
          </thead>
          <tbody>
            {invoices.map((inv) => (
              <tr
                key={inv.invoiceId}
                className="border-b border-gray-200 hover:bg-gray-50"
              >
                <td className="py-2 px-2">
                  <Link
                    to={`/billing/${inv.invoiceId}`}
                    className="text-blue-600 hover:underline"
                  >
                    {inv.invoiceId}
                  </Link>
                </td>
                <td className="py-2 px-2">{inv.bookingId}</td>
                <td className="py-2 px-2">{inv.shipperId}</td>
                <td className="py-2 px-2 text-right">
                  {Number(inv.totalAmount).toLocaleString('ja-JP')} {inv.currency}
                </td>
                <td className="py-2 px-2">
                  <span className="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800">
                    {billingStatusLabel(inv.billingStatus)}
                  </span>
                </td>
                <td className="py-2 px-2">{inv.paymentDue ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-end gap-2 text-sm">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="rounded border border-gray-300 px-3 py-1 disabled:opacity-50"
          >
            前へ
          </button>
          <span>
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="rounded border border-gray-300 px-3 py-1 disabled:opacity-50"
          >
            次へ
          </button>
        </div>
      )}
    </div>
  );
}
