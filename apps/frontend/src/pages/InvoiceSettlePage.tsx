import { useState } from 'react'
import { toast } from 'sonner'
import { useCalculateInvoice, useConfirmInvoice, useSettleInvoice } from '../features/billing/hooks/useBilling'
import type { InvoiceResponse, LineItemInput } from '../features/billing/types/billing'

const DEFAULT_LINE_ITEMS: LineItemInput[] = [
  { description: '基本料金', amountValue: 100000 },
]

function formatJpy(value: number): string {
  return value.toLocaleString('ja-JP') + ' 円'
}

export function InvoiceSettlePage() {
  const [bookingId, setBookingId] = useState('')
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null)

  const { mutate: calculate, isPending: isCalculating } = useCalculateInvoice()
  const { mutate: confirm, isPending: isConfirming } = useConfirmInvoice()
  const { mutate: settle, isPending: isSettling } = useSettleInvoice()

  const handleCalculate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!bookingId.trim()) {
      toast.error('予約 ID を入力してください。')
      return
    }
    calculate(
      { bookingId: bookingId.trim(), lineItems: DEFAULT_LINE_ITEMS },
      {
        onSuccess: (data) => {
          setInvoice(data)
        },
        onError: () => {
          toast.error('料金算出に失敗しました。')
        },
      },
    )
  }

  const handleConfirm = () => {
    if (!invoice) return
    confirm(invoice.id, {
      onSuccess: (data) => {
        setInvoice(data)
        toast.success('料金を確定しました。')
      },
      onError: () => {
        toast.error('料金確定に失敗しました。')
      },
    })
  }

  const handleSettle = () => {
    if (!invoice) return
    settle(invoice.id, {
      onSuccess: (data) => {
        setInvoice(data)
        toast.success('精算が完了しました。')
      },
      onError: () => {
        toast.error('精算処理に失敗しました。')
      },
    })
  }

  const statusLabel = (status: string) => {
    switch (status) {
      case 'PENDING': return '未確定'
      case 'CONFIRMED': return '確定済'
      case 'PAID': return '精算済'
      case 'OVERDUE': return '延滞'
      default: return status
    }
  }

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">精算処理</h1>

      <form onSubmit={handleCalculate} className="bg-white border border-gray-200 rounded-lg p-6 mb-6">
        <div className="flex gap-3 items-end">
          <div className="flex-1">
            <label htmlFor="bookingId" className="block text-sm font-medium text-gray-700 mb-1">
              予約 ID <span className="text-red-500">*</span>
            </label>
            <input
              id="bookingId"
              type="text"
              value={bookingId}
              onChange={(e) => setBookingId(e.target.value)}
              placeholder="例: BK-000001"
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <button
            type="submit"
            disabled={isCalculating}
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isCalculating ? '検索中...' : '精算書を取得'}
          </button>
        </div>
      </form>

      {invoice && (
        <div className="bg-white border border-gray-200 rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-gray-800">精算書</h2>
            <span
              className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                invoice.paymentStatus === 'PAID'
                  ? 'bg-blue-100 text-blue-800'
                  : invoice.paymentStatus === 'CONFIRMED'
                    ? 'bg-green-100 text-green-800'
                    : invoice.paymentStatus === 'OVERDUE'
                      ? 'bg-red-100 text-red-800'
                      : 'bg-yellow-100 text-yellow-800'
              }`}
            >
              {statusLabel(invoice.paymentStatus)}
            </span>
          </div>

          <div className="text-xs text-gray-500 mb-4 space-y-1">
            <p className="font-mono">請求書番号: {invoice.invoiceNumber}</p>
            <p>支払期限: {invoice.dueDate}</p>
            {invoice.paidAt && <p>入金日時: {invoice.paidAt}</p>}
          </div>

          <table className="w-full text-sm mb-6">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-1 text-gray-600 font-medium">項目</th>
                <th className="text-right py-1 text-gray-600 font-medium">金額</th>
              </tr>
            </thead>
            <tbody>
              {invoice.lineItems.map((item, i) => (
                <tr key={i} className="border-b border-gray-100">
                  <td className="py-2 text-gray-700">{item.description}</td>
                  <td className="py-2 text-right text-gray-700">{formatJpy(item.amountValue)}</td>
                </tr>
              ))}
              {invoice.discountAmountValue > 0 && (
                <tr className="border-b border-gray-100">
                  <td className="py-2 text-gray-600">割引（{(invoice.discountRate * 100).toFixed(0)}%）</td>
                  <td className="py-2 text-right text-red-600">-{formatJpy(invoice.discountAmountValue)}</td>
                </tr>
              )}
              <tr className="border-b border-gray-200 bg-gray-50">
                <td className="py-2 text-gray-600">消費税（10%）</td>
                <td className="py-2 text-right text-gray-600">
                  {formatJpy(invoice.finalAmountValue - (invoice.baseAmountValue - invoice.discountAmountValue))}
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td className="py-3 font-bold text-gray-900">請求金額</td>
                <td className="py-3 text-right font-bold text-gray-900 text-base">
                  {formatJpy(invoice.finalAmountValue)}
                </td>
              </tr>
            </tfoot>
          </table>

          <div className="flex justify-end gap-3">
            {invoice.paymentStatus === 'PENDING' && (
              <button
                type="button"
                onClick={handleConfirm}
                disabled={isConfirming}
                className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
              >
                {isConfirming ? '確定中...' : '料金を確定する'}
              </button>
            )}
            {invoice.paymentStatus === 'CONFIRMED' && (
              <button
                type="button"
                onClick={handleSettle}
                disabled={isSettling}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {isSettling ? '処理中...' : '入金確認・精算完了'}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
