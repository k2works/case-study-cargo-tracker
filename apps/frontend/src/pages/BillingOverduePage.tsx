import { Link } from 'react-router'
import { useOverdueInvoiceList } from '../features/billing/hooks/useBilling'

/**
 * S25: 督促一覧（経理担当者）。
 *
 * `/billing/overdue` でアクセス。INVOICED 状態かつ支払期限超過の Invoice を一覧表示する。
 */
export function BillingOverduePage() {
  const { data: invoices, isLoading, error } = useOverdueInvoiceList()

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const calcOverdueDays = (paymentDue: string | null): number => {
    if (!paymentDue) return 0
    const due = new Date(paymentDue)
    due.setHours(0, 0, 0, 0)
    return Math.max(0, Math.floor((today.getTime() - due.getTime()) / (1000 * 60 * 60 * 24)))
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold" data-testid="page-title">督促一覧</h1>
        <Link to="/billing" className="text-blue-600 hover:underline text-sm">
          ← 請求一覧へ
        </Link>
      </div>

      {isLoading && <div className="text-gray-500 text-sm">読み込み中...</div>}
      {error && <div className="text-red-600 text-sm">{(error as Error).message}</div>}
      {!isLoading && !error && invoices?.length === 0 && (
        <div className="bg-green-50 border border-green-200 text-green-700 rounded p-4 text-sm">
          支払期限超過の請求はありません。
        </div>
      )}
      {!isLoading && !error && invoices && invoices.length > 0 && (
        <div className="bg-white border rounded overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-semibold">請求 ID</th>
                <th className="text-left px-4 py-3 font-semibold">予約 ID</th>
                <th className="text-left px-4 py-3 font-semibold">荷主</th>
                <th className="text-right px-4 py-3 font-semibold">請求金額</th>
                <th className="text-left px-4 py-3 font-semibold">支払期限</th>
                <th className="text-right px-4 py-3 font-semibold">超過日数</th>
                <th className="text-left px-4 py-3 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {invoices.map((inv) => {
                const overdueDays = calcOverdueDays(inv.paymentDue)
                return (
                  <tr key={inv.invoiceId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs">{inv.invoiceId}</td>
                    <td className="px-4 py-3">{inv.bookingId}</td>
                    <td className="px-4 py-3">{inv.shipperId}</td>
                    <td className="px-4 py-3 text-right font-semibold">
                      {inv.totalAmount?.toLocaleString()} {inv.currency}
                    </td>
                    <td className="px-4 py-3">{inv.paymentDue ?? '—'}</td>
                    <td className="px-4 py-3 text-right">
                      <span className="text-red-600 font-semibold">{overdueDays} 日</span>
                    </td>
                    <td className="px-4 py-3">
                      <Link
                        to={`/billing/${inv.invoiceId}`}
                        className="text-blue-600 hover:underline"
                      >
                        詳細
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-4 text-xs text-gray-500">
        合計 {invoices?.length ?? 0} 件
      </div>
    </div>
  )
}
