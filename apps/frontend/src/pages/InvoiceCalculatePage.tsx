import { useState } from 'react'
import { toast } from 'sonner'
import { useCalculateInvoice, useConfirmInvoice } from '../features/billing/hooks/useBilling'
import type { InvoiceResponse, LineItemInput } from '../features/billing/types/billing'

const DEFAULT_LINE_ITEMS: LineItemInput[] = [
  { description: '基本料金', amountValue: 100000 },
  { description: '距離料金', amountValue: 0 },
]

function formatJpy(value: number): string {
  return value.toLocaleString('ja-JP') + ' 円'
}

export function InvoiceCalculatePage() {
  const [bookingId, setBookingId] = useState('')
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null)

  const { mutate: calculate, isPending: isCalculating } = useCalculateInvoice()
  const { mutate: confirm, isPending: isConfirming } = useConfirmInvoice()

  const handleCalculate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!bookingId.trim()) {
      toast.error('予約 ID を入力してください。')
      return
    }
    calculate(
      {
        bookingId: bookingId.trim(),
        lineItems: DEFAULT_LINE_ITEMS.filter((i) => i.amountValue > 0),
      },
      {
        onSuccess: (data) => {
          setInvoice(data)
          toast.success('料金を算出しました。')
        },
        onError: (err) => {
          toast.error(err instanceof Error ? err.message : '料金算出に失敗しました。')
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
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : '料金確定に失敗しました。')
      },
    })
  }

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">輸送料金算出</h1>

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
            {isCalculating ? '算出中...' : '料金算出'}
          </button>
        </div>
      </form>

      {invoice && (
        <div className="bg-white border border-gray-200 rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-gray-800">料金内訳</h2>
            <span
              className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                invoice.paymentStatus === 'CONFIRMED'
                  ? 'bg-green-100 text-green-800'
                  : 'bg-yellow-100 text-yellow-800'
              }`}
            >
              {invoice.paymentStatus === 'CONFIRMED' ? '確定済' : '算出済'}
            </span>
          </div>

          <p className="text-xs text-gray-500 mb-4 font-mono">
            予約 ID: {invoice.bookingId} | 請求書番号: {invoice.invoiceNumber}
          </p>

          <table className="w-full text-sm mb-4">
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
              <tr className="border-b border-gray-200 bg-gray-50">
                <td className="py-2 text-gray-600">消費税（10%）</td>
                <td className="py-2 text-right text-gray-600">
                  {formatJpy(invoice.finalAmountValue - invoice.baseAmountValue)}
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td className="py-3 font-bold text-gray-900">合計金額</td>
                <td className="py-3 text-right font-bold text-gray-900 text-base">
                  {formatJpy(invoice.finalAmountValue)}
                </td>
              </tr>
            </tfoot>
          </table>

          {invoice.paymentStatus === 'PENDING' && (
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setInvoice(null)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                disabled={isConfirming}
                className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
              >
                {isConfirming ? '確定中...' : '料金を確定する'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
