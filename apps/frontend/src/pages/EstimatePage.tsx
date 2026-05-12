import { useState } from 'react'
import { useCreateEstimate } from '../features/booking/hooks/useEstimate'
import type { Estimate } from '../features/booking/types/estimate'

const CARGO_TYPES = [
  { value: 'GENERAL', label: '一般貨物' },
  { value: 'HAZMAT', label: '危険物' },
  { value: 'REFRIGERATED', label: '冷凍貨物' },
]

export function EstimatePage() {
  const [form, setForm] = useState({
    originUnlocode: '',
    destinationUnlocode: '',
    arrivalDeadline: '',
    cargoType: 'GENERAL',
    weightKg: '',
  })
  const [result, setResult] = useState<Estimate | null>(null)
  const createEstimate = useCreateEstimate()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createEstimate.mutate(
      {
        originUnlocode: form.originUnlocode,
        destinationUnlocode: form.destinationUnlocode,
        arrivalDeadline: form.arrivalDeadline,
        cargoType: form.cargoType,
        weightKg: Number(form.weightKg),
      },
      { onSuccess: (data) => setResult(data) }
    )
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">輸送見積の作成</h1>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">出発地（LOCODE）</label>
            <input
              type="text"
              value={form.originUnlocode}
              onChange={(e) => setForm({ ...form, originUnlocode: e.target.value })}
              placeholder="JPTYO"
              required
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">目的地（LOCODE）</label>
            <input
              type="text"
              value={form.destinationUnlocode}
              onChange={(e) => setForm({ ...form, destinationUnlocode: e.target.value })}
              placeholder="USNYC"
              required
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">希望到着期限</label>
            <input
              type="date"
              value={form.arrivalDeadline}
              onChange={(e) => setForm({ ...form, arrivalDeadline: e.target.value })}
              required
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">貨物種別</label>
            <select
              value={form.cargoType}
              onChange={(e) => setForm({ ...form, cargoType: e.target.value })}
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            >
              {CARGO_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">重量（kg）</label>
            <input
              type="number"
              value={form.weightKg}
              onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
              min="0"
              step="0.001"
              required
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={createEstimate.isPending}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700 disabled:opacity-50"
          >
            {createEstimate.isPending ? '処理中...' : '見積を作成'}
          </button>
          {createEstimate.isError && (
            <p className="text-red-600 text-sm">見積の作成に失敗しました。</p>
          )}
        </form>

        {result && (
          <div className="mt-6">
            <p className="text-sm text-gray-600 mb-2">見積番号: {result.estimateId}</p>
            {result.status === 'NO_ROUTES' ? (
              <p className="text-yellow-700 text-sm">希望期限に間に合うルートが存在しません。</p>
            ) : (
              <>
                <h2 className="text-lg font-semibold text-gray-800 mb-2">ルート候補</h2>
                <table className="w-full text-sm border border-gray-200 rounded">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-3 py-2 text-left">航海番号</th>
                      <th className="px-3 py-2 text-left">経由港</th>
                      <th className="px-3 py-2 text-right">所要日数</th>
                      <th className="px-3 py-2 text-right">概算料金</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.candidates.map((c) => (
                      <tr key={c.rank} className="border-t border-gray-200">
                        <td className="px-3 py-2">{c.voyageNumber}</td>
                        <td className="px-3 py-2">{c.transitPort ?? '—'}</td>
                        <td className="px-3 py-2 text-right">{c.transitDays}日</td>
                        <td className="px-3 py-2 text-right">¥{c.estimatedCost.toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
