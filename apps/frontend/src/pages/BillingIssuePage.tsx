import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router'
import { useInvoice, useIssueInvoice } from '../features/billing/hooks/useBilling'

/**
 * S24: 精算書発行（経理担当者）。
 *
 * `/billing/:invoiceId/issue` でアクセス。CALCULATED 状態の Invoice に精算書を発行し、
 * 支払期限を設定して INVOICED 状態に遷移する。
 */
export function BillingIssuePage() {
  const { invoiceId } = useParams<{ invoiceId: string }>()
  const navigate = useNavigate()
  const { data: invoice, isLoading, error } = useInvoice(invoiceId ?? '')
  const issueInvoice = useIssueInvoice()

  const [paymentDue, setPaymentDue] = useState('')
  const [operatorId, setOperatorId] = useState('')
  const [errMsg, setErrMsg] = useState('')

  if (isLoading) return <div className="p-6">読み込み中...</div>
  if (error) return <div className="p-6 text-red-600">{(error as Error).message}</div>
  if (!invoice) return <div className="p-6">請求情報が見つかりません。</div>

  if (invoice.billingStatus !== 'CALCULATED') {
    return (
      <div className="p-6">
        <div className="bg-yellow-50 border border-yellow-200 text-yellow-700 rounded p-3 text-sm mb-4">
          精算書発行は CALCULATED 状態の請求にのみ実行できます。現在の状態: {invoice.billingStatus}
        </div>
        <Link to={`/billing/${invoiceId}`} className="text-blue-600 hover:underline">
          ← 請求詳細へ戻る
        </Link>
      </div>
    )
  }

  const handleIssue = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrMsg('')
    try {
      await issueInvoice.mutateAsync({
        invoiceId: invoice.invoiceId,
        body: {
          paymentDue: paymentDue || null,
          operatorId: operatorId || 'system',
        },
      })
      navigate(`/billing/${invoiceId}`)
    } catch (err) {
      setErrMsg((err as Error).message)
    }
  }

  return (
    <div className="p-6 max-w-lg">
      <div className="flex items-center gap-4 mb-4">
        <Link to={`/billing/${invoiceId}`} className="text-blue-600 hover:underline text-sm">
          ← 請求詳細へ
        </Link>
        <h1 className="text-2xl font-bold">精算書発行</h1>
      </div>

      <div className="bg-white border rounded p-4 mb-4">
        <h2 className="font-semibold mb-3 text-sm text-gray-500">精算書プレビュー</h2>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-gray-500">請求 ID</dt>
          <dd className="font-mono text-xs">{invoice.invoiceId}</dd>
          <dt className="text-gray-500">予約 ID</dt>
          <dd>{invoice.bookingId}</dd>
          <dt className="text-gray-500">荷主</dt>
          <dd>{invoice.shipperId}</dd>
          <dt className="text-gray-500">請求金額</dt>
          <dd className="font-semibold">
            {invoice.totalAmount?.toLocaleString()} {invoice.currency}
          </dd>
        </dl>
      </div>

      <div className="bg-white border rounded p-4 mb-4">
        <form onSubmit={handleIssue} className="space-y-3">
          <div>
            <label className="block text-sm text-gray-600 mb-1">支払期限</label>
            <input
              type="date"
              value={paymentDue}
              onChange={(e) => setPaymentDue(e.target.value)}
              className="border rounded px-3 py-1.5 w-full text-sm"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">操作者 ID</label>
            <input
              type="text"
              value={operatorId}
              onChange={(e) => setOperatorId(e.target.value)}
              className="border rounded px-3 py-1.5 w-full text-sm"
              placeholder="例: admin-001"
            />
          </div>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={issueInvoice.isPending}
              className="bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700 disabled:opacity-50"
            >
              {issueInvoice.isPending ? '発行中...' : '精算書を発行'}
            </button>
            <Link
              to={`/billing/${invoiceId}`}
              className="px-4 py-2 rounded text-sm border hover:bg-gray-50"
            >
              キャンセル
            </Link>
          </div>
        </form>
      </div>

      {errMsg && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm">
          {errMsg}
        </div>
      )}
    </div>
  )
}
