import { useState } from 'react'
import { useCreateVoyage, useUpdateVoyage } from '../hooks/useVoyages'
import type { Voyage, CarrierMovement } from '../types/voyage'

interface VoyageFormProps {
  voyage?: Voyage
  onSuccess?: () => void
  onCancel?: () => void
}

const emptyMovement = (): CarrierMovement => ({
  departureLocationUnlocode: '',
  arrivalLocationUnlocode: '',
  departureDate: '',
  arrivalDate: '',
  seqNumber: 0,
})

export function VoyageForm({ voyage, onSuccess, onCancel }: VoyageFormProps) {
  const isEdit = !!voyage
  const [voyageNumber, setVoyageNumber] = useState(voyage?.voyageNumber ?? '')
  const [movements, setMovements] = useState<CarrierMovement[]>(
    voyage?.carrierMovements ?? [emptyMovement()]
  )

  const createMutation = useCreateVoyage()
  const updateMutation = useUpdateVoyage(voyage?.voyageNumber ?? '')
  const isPending = createMutation.isPending || updateMutation.isPending

  const handleMovementChange = (index: number, field: keyof CarrierMovement, value: string | number) => {
    setMovements((prev) => prev.map((m, i) => (i === index ? { ...m, [field]: value } : m)))
  }

  const addMovement = () => {
    setMovements((prev) => [...prev, { ...emptyMovement(), seqNumber: prev.length }])
  }

  const removeMovement = (index: number) => {
    setMovements((prev) => prev.filter((_, i) => i !== index))
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const payload = { carrierMovements: movements }
    if (isEdit) {
      updateMutation.mutate(payload, { onSuccess })
    } else {
      createMutation.mutate({ voyageNumber, ...payload }, { onSuccess })
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {!isEdit && (
        <div>
          <label htmlFor="voyageNumber" className="block text-sm font-medium text-gray-700 mb-1">
            航海番号
          </label>
          <input
            id="voyageNumber"
            value={voyageNumber}
            onChange={(e) => setVoyageNumber(e.target.value)}
            required
            placeholder="V0045"
            className="border border-gray-300 rounded-md px-3 py-2 text-sm w-48 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
      )}

      <div>
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-semibold text-gray-700">運航区間</h3>
        </div>
        <div className="overflow-x-auto border border-gray-200 rounded-lg">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">#</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">出発港</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">到着港</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">出発予定</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">到着予定</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">操作</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {movements.map((m, i) => (
                <tr key={i}>
                  <td className="px-3 py-2 text-sm text-gray-700">{i + 1}</td>
                  <td className="px-3 py-2">
                    <input
                      aria-label="出発地 UN/LOCODE"
                      placeholder="出発地 UN/LOCODE"
                      value={m.departureLocationUnlocode}
                      onChange={(e) => handleMovementChange(i, 'departureLocationUnlocode', e.target.value)}
                      required
                      className="border border-gray-300 rounded px-2 py-1 text-sm w-24 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      aria-label="到着地 UN/LOCODE"
                      placeholder="到着地 UN/LOCODE"
                      value={m.arrivalLocationUnlocode}
                      onChange={(e) => handleMovementChange(i, 'arrivalLocationUnlocode', e.target.value)}
                      required
                      className="border border-gray-300 rounded px-2 py-1 text-sm w-24 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="datetime-local"
                      aria-label="出発日時"
                      value={m.departureDate.slice(0, 16)}
                      onChange={(e) => handleMovementChange(i, 'departureDate', e.target.value + ':00+09:00')}
                      required
                      className="border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="datetime-local"
                      aria-label="到着日時"
                      value={m.arrivalDate.slice(0, 16)}
                      onChange={(e) => handleMovementChange(i, 'arrivalDate', e.target.value + ':00+09:00')}
                      required
                      className="border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
                    />
                  </td>
                  <td className="px-3 py-2">
                    <button
                      type="button"
                      onClick={() => removeMovement(i)}
                      className="text-red-600 hover:text-red-800 text-sm font-medium"
                    >
                      削除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button
          type="button"
          onClick={addMovement}
          className="mt-2 text-sm text-blue-600 hover:text-blue-800 font-medium"
        >
          区間を追加
        </button>
      </div>

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={isPending}
          className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {isEdit ? '更新' : '登録'}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md bg-gray-100 px-6 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
          >
            キャンセル
          </button>
        )}
      </div>
    </form>
  )
}
