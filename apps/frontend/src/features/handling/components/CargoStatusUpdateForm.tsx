import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  useCargoSnapshot,
  useStatusHistory,
  useUpdateCargoStatus,
} from '../hooks/useHandling'
import {
  CARGO_STATUS_LABELS,
  type CargoStatus,
  type UpdateCargoStatusRequest,
} from '../types/handling'

interface FormState {
  newStatus: CargoStatus
  unlocode: string
  updatedAt: string
  operatorId: string
}

const INITIAL_STATE: FormState = {
  newStatus: 'IN_TRANSIT',
  unlocode: '',
  updatedAt: '',
  operatorId: '',
}

export function CargoStatusUpdateForm() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>()
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState>(INITIAL_STATE)
  const [error, setError] = useState<string | null>(null)

  const { data: snapshot, isLoading: snapshotLoading, isError: snapshotError } =
    useCargoSnapshot(trackingNumber)
  const { data: history } = useStatusHistory(trackingNumber)
  const update = useUpdateCargoStatus(trackingNumber)

  const handleSubmit = (e: { preventDefault(): void }) => {
    e.preventDefault()
    setError(null)
    const request: UpdateCargoStatusRequest = {
      newStatus: form.newStatus,
      unlocode: form.unlocode.toUpperCase(),
      updatedAt: form.updatedAt,
      operatorId: form.operatorId,
    }
    update.mutate(request, {
      onSuccess: () => {
        setForm(INITIAL_STATE)
      },
      onError: (err: unknown) => {
        if (err instanceof Error) {
          setError(err.message)
        } else {
          setError('更新に失敗しました')
        }
      },
    })
  }

  if (snapshotLoading) {
    return <div className="text-sm text-gray-500">読み込み中...</div>
  }
  if (snapshotError || !snapshot) {
    return (
      <div
        className="rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700"
        role="alert"
        data-testid="snapshot-error"
      >
        追跡番号 {trackingNumber} の貨物情報が見つかりません。
      </div>
    )
  }

  return (
    <div className="space-y-6" data-testid="cargo-status-update">
      <section className="bg-white border border-gray-200 rounded-md p-4">
        <h2 className="text-lg font-semibold mb-2">現在の貨物情報</h2>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-gray-500">追跡番号</dt>
          <dd className="font-mono" data-testid="snapshot-tracking">
            {snapshot.trackingNumber}
          </dd>
          <dt className="text-gray-500">予約 ID</dt>
          <dd>{snapshot.bookingId}</dd>
          <dt className="text-gray-500">出発地 → 到着地</dt>
          <dd>
            {snapshot.originUnlocode} → {snapshot.destinationUnlocode}
          </dd>
          <dt className="text-gray-500">貨物種別</dt>
          <dd>{snapshot.cargoType}</dd>
        </dl>
      </section>

      <form onSubmit={handleSubmit} className="space-y-4" data-testid="status-update-form">
        <h2 className="text-lg font-semibold">状態を更新</h2>
        <div>
          <label htmlFor="status-new" className="block text-sm font-medium text-gray-700">
            新しい状態
          </label>
          <select
            id="status-new"
            value={form.newStatus}
            onChange={(e) => setForm({ ...form, newStatus: e.target.value as CargoStatus })}
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            data-testid="status-new-select"
          >
            {(Object.keys(CARGO_STATUS_LABELS) as CargoStatus[]).map((s) => (
              <option key={s} value={s}>
                {CARGO_STATUS_LABELS[s]}（{s}）
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="status-unlocode" className="block text-sm font-medium text-gray-700">
            現在位置（UN/LOCODE）
          </label>
          <input
            id="status-unlocode"
            type="text"
            value={form.unlocode}
            onChange={(e) => setForm({ ...form, unlocode: e.target.value })}
            required
            maxLength={5}
            placeholder="SGSIN"
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm uppercase"
            data-testid="status-unlocode-input"
          />
        </div>

        <div>
          <label htmlFor="status-updated-at" className="block text-sm font-medium text-gray-700">
            更新日時
          </label>
          <input
            id="status-updated-at"
            type="datetime-local"
            value={form.updatedAt}
            onChange={(e) => setForm({ ...form, updatedAt: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            data-testid="status-updated-at-input"
          />
        </div>

        <div>
          <label htmlFor="status-operator" className="block text-sm font-medium text-gray-700">
            追跡管理者 ID
          </label>
          <input
            id="status-operator"
            type="text"
            value={form.operatorId}
            onChange={(e) => setForm({ ...form, operatorId: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            data-testid="status-operator-input"
          />
        </div>

        {error && (
          <div
            className="rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700"
            role="alert"
            data-testid="status-error"
          >
            {error}
          </div>
        )}

        {update.isSuccess && (
          <div
            className="rounded-md bg-green-50 border border-green-200 px-4 py-3 text-sm text-green-700"
            role="status"
            data-testid="status-success"
          >
            貨物状態を更新しました。
          </div>
        )}

        <div className="flex space-x-2">
          <button
            type="submit"
            disabled={update.isPending}
            className="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm disabled:opacity-50"
            data-testid="status-submit"
          >
            {update.isPending ? '更新中...' : '更新'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/handling')}
            className="bg-gray-200 text-gray-800 px-4 py-2 rounded-md text-sm"
          >
            一覧に戻る
          </button>
        </div>
      </form>

      <section data-testid="status-history-section">
        <h2 className="text-lg font-semibold mb-2">追跡イベント履歴（状態手動更新）</h2>
        {!history || history.length === 0 ? (
          <p className="text-sm text-gray-500">手動更新履歴はまだありません。</p>
        ) : (
          <table className="min-w-full text-sm border border-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-3 py-2 text-left">更新日時</th>
                <th className="px-3 py-2 text-left">状態</th>
                <th className="px-3 py-2 text-left">場所</th>
                <th className="px-3 py-2 text-left">管理者</th>
              </tr>
            </thead>
            <tbody>
              {history.map((h) => (
                <tr key={h.historyId} className="border-t border-gray-100">
                  <td className="px-3 py-2">{h.updatedAt}</td>
                  <td className="px-3 py-2">
                    {CARGO_STATUS_LABELS[h.newStatus as CargoStatus] ?? h.newStatus}
                  </td>
                  <td className="px-3 py-2">{h.unlocode}</td>
                  <td className="px-3 py-2">{h.operatorId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
