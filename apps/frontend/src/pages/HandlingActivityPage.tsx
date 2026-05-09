import { useState } from 'react'
import { useRecordHandlingActivity } from '../features/tracking/hooks/useTracking'

const EVENT_TYPE_LABELS: Record<string, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CUSTOMS: '通関',
  CLAIM: '引渡し',
}

export function HandlingActivityPage() {
  const { mutate: recordActivity, isPending } = useRecordHandlingActivity()

  const [form, setForm] = useState({
    trackingNumber: '',
    eventType: 'RECEIVE',
    locationUnlocode: '',
    eventTime: '',
    voyageNumber: '',
  })
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
    setSuccessMessage(null)
    setErrorMessage(null)
  }

  const handleClear = () => {
    setForm({
      trackingNumber: '',
      eventType: 'RECEIVE',
      locationUnlocode: '',
      eventTime: '',
      voyageNumber: '',
    })
    setSuccessMessage(null)
    setErrorMessage(null)
  }

  const clearExceptTrackingNumber = () => {
    setForm((prev) => ({
      ...prev,
      eventType: 'RECEIVE',
      locationUnlocode: '',
      eventTime: '',
      voyageNumber: '',
    }))
    setSuccessMessage(null)
    setErrorMessage(null)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.trackingNumber || !form.locationUnlocode || !form.eventTime) {
      setErrorMessage('追跡番号・作業場所・作業日時は必須です。')
      return
    }

    recordActivity(
      {
        trackingNumber: form.trackingNumber,
        eventType: form.eventType,
        locationUnlocode: form.locationUnlocode,
        eventTime: form.eventTime,
        voyageNumber: form.voyageNumber || undefined,
      },
      {
        onSuccess: (data) => {
          setSuccessMessage(
            `荷役作業を記録しました。追跡番号: ${data.trackingNumber} / 状態: ${data.transportStatus}`,
          )
          clearExceptTrackingNumber()
        },
        onError: (err) => {
          setErrorMessage(
            err instanceof Error ? err.message : '荷役作業の記録に失敗しました。',
          )
        },
      },
    )
  }

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">荷役作業記録</h1>

      {successMessage && (
        <div className="mb-4 rounded-md bg-green-50 border border-green-200 p-3 text-sm text-green-800">
          {successMessage}
        </div>
      )}
      {errorMessage && (
        <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-3 text-sm text-red-800">
          {errorMessage}
        </div>
      )}

      <form
        onSubmit={handleSubmit}
        className="bg-white border border-gray-200 rounded-lg p-6 space-y-4"
      >
        <div>
          <label
            className="block text-sm font-medium text-gray-700 mb-1"
            htmlFor="trackingNumber"
          >
            追跡番号 <span className="text-red-500">*</span>
          </label>
          <input
            id="trackingNumber"
            name="trackingNumber"
            type="text"
            value={form.trackingNumber}
            onChange={handleChange}
            placeholder="TRK-000001"
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="eventType">
            作業種別 <span className="text-red-500">*</span>
          </label>
          <select
            id="eventType"
            name="eventType"
            value={form.eventType}
            onChange={handleChange}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            {Object.entries(EVENT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label
            className="block text-sm font-medium text-gray-700 mb-1"
            htmlFor="locationUnlocode"
          >
            作業場所（UN/LOCODE）<span className="text-red-500">*</span>
          </label>
          <input
            id="locationUnlocode"
            name="locationUnlocode"
            type="text"
            value={form.locationUnlocode}
            onChange={handleChange}
            placeholder="JPTYO"
            maxLength={5}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="eventTime">
            作業日時 <span className="text-red-500">*</span>
          </label>
          <input
            id="eventTime"
            name="eventTime"
            type="datetime-local"
            value={form.eventTime}
            onChange={handleChange}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="voyageNumber">
            航路番号（任意）
          </label>
          <input
            id="voyageNumber"
            name="voyageNumber"
            type="text"
            value={form.voyageNumber}
            onChange={handleChange}
            placeholder="V0042"
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={isPending}
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isPending ? '記録中...' : '記録する'}
          </button>
          <button
            type="button"
            onClick={handleClear}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            クリア
          </button>
        </div>
      </form>
    </div>
  )
}
