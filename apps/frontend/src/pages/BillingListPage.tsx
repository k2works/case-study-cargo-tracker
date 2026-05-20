import { Link } from 'react-router'
import { useInvoiceList } from '../features/billing/hooks/useBilling'

/**
 * S22: 請求一覧（経理担当者）。
 *
 * `/billing` でアクセス。Invoice 一覧を BillingStatus フィルタ付きで表示し、
 * S23 請求詳細（`/billing/:invoiceId`）へ遷移する。
 */
export function BillingListPage() {
  const { data, isLoading, error } = useInvoiceList()

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">請求一覧</h1>
      <p className="text-sm text-gray-500 mb-4">
        輸送料金の請求一覧です。行をクリックして詳細・料金算出へ移動します。
      </p>
      {isLoading && <div className="text-gray-500 text-sm">読み込み中...</div>}
      {error && <div className="text-red-600 text-sm">{(error as Error).message}</div>}
      {!isLoading && !error && data?.length === 0 && (
        <p className="text-gray-500">請求情報がありません。</p>
      )}
      {!isLoading && !error && data && data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full border border-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left border-b">請求 ID</th>
                <th className="px-4 py-2 text-left border-b">予約 ID</th>
                <th className="px-4 py-2 text-left border-b">荷主</th>
                <th className="px-4 py-2 text-right border-b">請求金額</th>
                <th className="px-4 py-2 text-left border-b">ステータス</th>
                <th className="px-4 py-2 text-left border-b">支払期限</th>
                <th className="px-4 py-2 text-left border-b">操作</th>
              </tr>
            </thead>
            <tbody>
              {data.map((inv) => (
                <tr key={inv.invoiceId} className="hover:bg-gray-50">
                  <td className="px-4 py-2 border-b font-mono text-xs">
                    {inv.invoiceId.slice(0, 8)}
                  </td>
                  <td className="px-4 py-2 border-b">{inv.bookingId}</td>
                  <td className="px-4 py-2 border-b">{inv.shipperId}</td>
                  <td className="px-4 py-2 border-b text-right">
                    {inv.totalAmount != null
                      ? `${inv.totalAmount.toLocaleString()} ${inv.currency}`
                      : '-'}
                  </td>
                  <td className="px-4 py-2 border-b">
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-medium ${statusColor(inv.billingStatus)}`}
                    >
                      {inv.billingStatus}
                    </span>
                  </td>
                  <td className="px-4 py-2 border-b">{inv.paymentDue ?? '-'}</td>
                  <td className="px-4 py-2 border-b">
                    <Link
                      to={`/billing/${inv.invoiceId}`}
                      className="text-blue-600 hover:underline"
                    >
                      詳細
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function statusColor(status: string): string {
  switch (status) {
    case 'PENDING':
      return 'bg-gray-100 text-gray-700'
    case 'CALCULATED':
      return 'bg-blue-100 text-blue-700'
    case 'INVOICED':
      return 'bg-yellow-100 text-yellow-700'
    case 'PAID':
      return 'bg-green-100 text-green-700'
    case 'OVERDUE':
      return 'bg-red-100 text-red-700'
    case 'CANCELLED':
      return 'bg-gray-200 text-gray-500'
    default:
      return 'bg-gray-100 text-gray-700'
  }
}
