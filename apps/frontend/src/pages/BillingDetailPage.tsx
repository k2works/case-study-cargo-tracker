import { useState } from 'react'
import { useParams, Link } from 'react-router'
import { useInvoice, useCalculateCharge } from '../features/billing/hooks/useBilling'

/**
 * S23: 請求詳細・算出（経理担当者）。
 *
 * `/billing/:invoiceId` でアクセス。Invoice 詳細を表示し、
 * PENDING 状態の場合は料金算出フォームを提供する。
 */
export function BillingDetailPage() {
  const { invoiceId } = useParams<{ invoiceId: string }>()
  const { data: invoice, isLoading, error } = useInvoice(invoiceId ?? '')
  const calculate = useCalculateCharge()

  const [baseAmount, setBaseAmount] = useState('')
  const [discountRate, setDiscountRate] = useState('0')
  const [operatorId, setOperatorId] = useState('')
  const [successMsg, setSuccessMsg] = useState('')
  const [errMsg, setErrMsg] = useState('')

  if (isLoading) return <div className="p-6">読み込み中...</div>
  if (error) return <div className="p-6 text-red-600">{(error as Error).message}</div>
  if (!invoice) return <div className="p-6">請求情報が見つかりません。</div>

  const handleCalculate = async (e: React.FormEvent) => {
    e.preventDefault()
    setSuccessMsg('')
    setErrMsg('')
    try {
      await calculate.mutateAsync({
        invoiceId: invoice.invoiceId,
        body: {
          baseAmount: Number(baseAmount),
          currencyCode: invoice.currency || 'JPY',
          discountRate: Number(discountRate),
          operatorId: operatorId || 'system',
        },
      })
      setSuccessMsg('輸送料金を算出しました。内容を確認して精算書を発行してください。')
    } catch (err) {
      setErrMsg((err as Error).message)
    }
  }

  return (
    <div className="p-6 max-w-2xl">
      <div className="flex items-center gap-4 mb-4">
        <Link to="/billing" className="text-blue-600 hover:underline text-sm">
          ← 請求一覧へ
        </Link>
        <h1 className="text-2xl font-bold">請求詳細</h1>
      </div>

      <div className="bg-white border rounded p-4 mb-4">
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-gray-500">請求 ID</dt>
          <dd className="font-mono text-xs">{invoice.invoiceId}</dd>
          <dt className="text-gray-500">予約 ID</dt>
          <dd>{invoice.bookingId}</dd>
          <dt className="text-gray-500">荷主</dt>
          <dd>{invoice.shipperId}</dd>
          <dt className="text-gray-500">現在ステータス</dt>
          <dd>{invoice.billingStatus}</dd>
          {invoice.basicAmount != null && (
            <>
              <dt className="text-gray-500">基本料金</dt>
              <dd>
                {invoice.basicAmount.toLocaleString()} {invoice.currency}
              </dd>
            </>
          )}
          {invoice.discountAmount != null && invoice.discountAmount > 0 && (
            <>
              <dt className="text-gray-500">割引額</dt>
              <dd className="text-red-600">
                -{invoice.discountAmount.toLocaleString()} {invoice.currency}
              </dd>
            </>
          )}
          {invoice.totalAmount != null && (
            <>
              <dt className="text-gray-500 font-semibold">請求金額（割引後）</dt>
              <dd className="font-semibold">
                {invoice.totalAmount.toLocaleString()} {invoice.currency}
              </dd>
            </>
          )}
          {invoice.paymentDue && (
            <>
              <dt className="text-gray-500">支払期限</dt>
              <dd>{invoice.paymentDue}</dd>
            </>
          )}
        </dl>
      </div>

      {invoice.billingStatus === 'PENDING' && (
        <div className="bg-white border rounded p-4 mb-4">
          <h2 className="font-semibold mb-3">料金算出</h2>
          <form onSubmit={handleCalculate} className="space-y-3">
            <div>
              <label className="block text-sm text-gray-600 mb-1">基本料金（JPY）</label>
              <input
                type="number"
                value={baseAmount}
                onChange={(e) => setBaseAmount(e.target.value)}
                required
                min={0}
                className="border rounded px-3 py-1.5 w-full text-sm"
                placeholder="例: 115000"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">割引率（0〜0.30）</label>
              <input
                type="number"
                value={discountRate}
                onChange={(e) => setDiscountRate(e.target.value)}
                step="0.01"
                min={0}
                max={0.3}
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
            <button
              type="submit"
              disabled={calculate.isPending}
              className="bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700 disabled:opacity-50"
            >
              {calculate.isPending ? '算出中...' : '料金を算出・確定'}
            </button>
          </form>
        </div>
      )}

      {invoice.billingStatus === 'CALCULATED' && (
        <div className="mb-4">
          <Link
            to={`/billing/${invoiceId}/issue`}
            className="inline-block bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700"
          >
            精算書を発行
          </Link>
        </div>
      )}

      {successMsg && (
        <div className="bg-green-50 border border-green-200 text-green-700 rounded p-3 text-sm mb-4">
          {successMsg}
        </div>
      )}
      {errMsg && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm mb-4">
          {errMsg}
        </div>
      )}
    </div>
  )
}
