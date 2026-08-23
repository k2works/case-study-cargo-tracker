import { useState } from 'react'
import type { HandlingLocation } from '../../handling/types'
import type { TrackingStatusChoice } from '../types'

type Props = {
  statuses: TrackingStatusChoice[]
  locations: HandlingLocation[]
  pending: boolean
  onSubmit: (input: { status: string; locationUnLocode: string; occurredAt: string }) => void
}

/**
 * 状態を手で反映する（US17-2）。
 *
 * **押せるのに断られる操作を出さない。** 進める先の選択肢はサーバが返す
 * （[ADR-024] 決定 1）。画面が全状態を並べて 409 を受けるのは、断られる操作を
 * 出していることと同じである。
 */
export function ManualUpdateForm({ statuses, locations, pending, onSubmit }: Props) {
  const [status, setStatus] = useState('')
  const [locationUnLocode, setLocationUnLocode] = useState('')
  const [occurredAt, setOccurredAt] = useState('')

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit({ status, locationUnLocode, occurredAt })
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded border border-gray-200 p-4">
      <h2 className="text-lg font-semibold text-gray-900">状態を手で反映する</h2>
      <p className="text-sm text-gray-600">
        出港・入港など、<strong>荷役の記録では捕捉できない</strong>変化を反映します。
        {/* 改行を空白と読ませない（日本語は語間を空けない） */}
        前の状態には戻せません。
      </p>
      <div className="grid gap-4 md:grid-cols-3">
        <div>
          <label htmlFor="status" className="block text-sm font-medium text-gray-700">
            新しい状態
          </label>
          <select
            id="status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          >
            <option value="">選んでください</option>
            {statuses.map((choice) => (
              <option key={choice.status} value={choice.status}>
                {choice.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="location" className="block text-sm font-medium text-gray-700">
            現在地
          </label>
          <select
            id="location"
            value={locationUnLocode}
            onChange={(event) => setLocationUnLocode(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          >
            <option value="">選んでください</option>
            {locations.map((location) => (
              <option key={location.unLocode} value={location.unLocode}>
                {location.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="occurredAt" className="block text-sm font-medium text-gray-700">
            日時
          </label>
          <input
            id="occurredAt"
            type="datetime-local"
            value={occurredAt}
            onChange={(event) => setOccurredAt(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>
      </div>
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
      >
        状態を更新する
      </button>
    </form>
  )
}
