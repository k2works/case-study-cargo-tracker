import { useState } from 'react'
import { useParams } from 'react-router'
import { toast } from 'sonner'
import {
  useTrackingActivity,
  useRecordTrackingException,
} from '../features/tracking/hooks/useTracking'

const EXCEPTION_TYPE_LABELS: Record<string, string> = {
  DELAY: '遅延',
  CUSTOMS_HOLD: '通関保留',
  DAMAGE: '損傷',
  LOST: '紛失',
  OTHER: 'その他',
}

const EXCEPTION_TYPES = Object.keys(EXCEPTION_TYPE_LABELS)

export function TrackingExceptionPage() {
  const { trackingNumber } = useParams<{ trackingNumber: string }>()
  const { data: activity, isLoading, isError } = useTrackingActivity(trackingNumber ?? '')
  const { mutate: recordException, isPending } = useRecordTrackingException(trackingNumber ?? '')

  const [form, setForm] = useState({
    exceptionType: 'DELAY',
    locationUnlocode: '',
    reason: '',
    escalationFlag: false,
  })

  if (isLoading) return <p className="p-6 text-center text-gray-500">読み込み中...</p>
  if (isError || !activity)
    return <p className="p-6 text-center text-red-500">追跡情報が見つかりません。</p>

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>,
  ) => {
    const { name, value, type } = e.target
    if (type === 'checkbox') {
      setForm((prev) => ({ ...prev, [name]: (e.target as HTMLInputElement).checked }))
    } else {
      setForm((prev) => ({ ...prev, [name]: value }))
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    if (!form.reason.trim()) {
      toast.error('遅延・例外理由を入力してください。')
      return
    }

    recordException(
      {
        exceptionType: form.exceptionType,
        occurredAt: new Date().toISOString(),
        locationUnlocode: form.locationUnlocode || undefined,
        reason: form.reason,
        escalationFlag: form.escalationFlag,
      },
      {
        onSuccess: () => {
          toast.success('遅延例外を記録しました。貨物状態が EXCEPTION に更新されました。')
          setForm({ exceptionType: 'DELAY', locationUnlocode: '', reason: '', escalationFlag: false })
        },
        onError: (err) => {
          toast.error(err instanceof Error ? err.message : '遅延例外の記録に失敗しました。')
        },
      },
    )
  }

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">遅延例外記録</h1>
      <p className="text-sm text-gray-500 mb-6 font-mono">{activity.trackingNumber}</p>

      <div className="bg-white border border-gray-200 rounded-lg p-4 mb-6">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-gray-800">現在の状態</h2>
          <span className="inline-flex px-2 py-0.5 rounded-full text-sm font-medium bg-blue-100 text-blue-800">
            {activity.transportStatus}
          </span>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <h2 className="text-base font-semibold text-gray-800 mb-4">例外情報を記録する</h2>

        <div>
          <label htmlFor="exceptionType" className="block text-sm font-medium text-gray-700 mb-1">
            例外種別 <span className="text-red-500">*</span>
          </label>
          <select
            id="exceptionType"
            name="exceptionType"
            value={form.exceptionType}
            onChange={handleChange}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            {EXCEPTION_TYPES.map((type) => (
              <option key={type} value={type}>
                {EXCEPTION_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="locationUnlocode" className="block text-sm font-medium text-gray-700 mb-1">
            発生場所（UN/LOCODE）
          </label>
          <input
            id="locationUnlocode"
            name="locationUnlocode"
            type="text"
            value={form.locationUnlocode}
            onChange={handleChange}
            placeholder="例: JPTYO"
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label htmlFor="reason" className="block text-sm font-medium text-gray-700 mb-1">
            遅延・例外理由 <span className="text-red-500">*</span>
          </label>
          <textarea
            id="reason"
            name="reason"
            value={form.reason}
            onChange={handleChange}
            rows={3}
            placeholder="遅延・例外の理由を入力してください"
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div className="flex items-center gap-2">
          <input
            id="escalationFlag"
            name="escalationFlag"
            type="checkbox"
            checked={form.escalationFlag}
            onChange={handleChange}
            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
          />
          <label htmlFor="escalationFlag" className="text-sm font-medium text-gray-700">
            エスカレーション対象
          </label>
        </div>

        <div className="flex justify-end pt-2">
          <button
            type="submit"
            disabled={isPending}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
          >
            {isPending ? '記録中...' : '例外を記録する'}
          </button>
        </div>
      </form>
    </div>
  )
}
