import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { type Invoice, fetchOverdueInvoices } from '../api/billingApi';

/**
 * S25 督促一覧画面（US23、IT7 T4.7、ROLE_ACCOUNTANT）。
 *
 * <p>iteration_plan-7 §UI 設計 S25 / ui_design.md L112-115 準拠。INVOICED かつ
 * payment_due 超過の請求書のみを表示する。OverdueScheduler（T4.6）が毎日 09:00 JST に
 * 自動で MarkOverdueCommand を発火するが、本画面では未督促含む候補をリアルタイムに参照可能。</p>
 */
export default function OverdueListPage() {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const promise = fetchOverdueInvoices();
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
  }, []);

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <h1 className="text-2xl font-bold mb-4">督促一覧</h1>
      <p className="mb-4 text-sm text-gray-600">
        INVOICED 状態で支払期限を超過した請求書（合計 {totalCount} 件）。
        OverdueScheduler が毎日 09:00 JST に自動で督促処理を実施します。
      </p>

      {error && (
        <p className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
      )}

      {loading ? (
        <p className="text-gray-500">読み込み中…</p>
      ) : invoices.length === 0 ? (
        <p className="rounded bg-green-50 px-3 py-2 text-sm text-green-700">
          督促対象の請求書はありません
        </p>
      ) : (
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="border-b border-gray-300 text-left">
              <th className="py-2 px-2">請求書 ID</th>
              <th className="py-2 px-2">請求書番号</th>
              <th className="py-2 px-2">荷主 ID</th>
              <th className="py-2 px-2 text-right">合計金額</th>
              <th className="py-2 px-2">支払期限（超過）</th>
            </tr>
          </thead>
          <tbody>
            {invoices.map((inv) => (
              <tr
                key={inv.invoiceId}
                className="border-b border-gray-200 hover:bg-red-50"
              >
                <td className="py-2 px-2">
                  <Link
                    to={`/billing/${inv.invoiceId}`}
                    className="text-blue-600 hover:underline"
                  >
                    {inv.invoiceId}
                  </Link>
                </td>
                <td className="py-2 px-2">{inv.invoiceNumber ?? '-'}</td>
                <td className="py-2 px-2">{inv.shipperId}</td>
                <td className="py-2 px-2 text-right">
                  {Number(inv.totalAmount).toLocaleString('ja-JP')} {inv.currency}
                </td>
                <td className="py-2 px-2 text-red-600">{inv.paymentDue ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
