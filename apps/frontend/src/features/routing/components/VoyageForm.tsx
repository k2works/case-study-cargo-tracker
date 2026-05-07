import { useState } from 'react'
import { useCreateVoyage, useUpdateVoyage } from '../hooks/useVoyages'
import type { Voyage, CarrierMovement } from '../types/voyage'

interface VoyageFormProps {
  voyage?: Voyage
  onSuccess?: () => void
}

const emptyMovement = (): CarrierMovement => ({
  departureLocationUnlocode: '',
  arrivalLocationUnlocode: '',
  departureDate: '',
  arrivalDate: '',
  seqNumber: 0,
})

export function VoyageForm({ voyage, onSuccess }: VoyageFormProps) {
  const isEdit = !!voyage
  const [voyageNumber, setVoyageNumber] = useState(voyage?.voyageNumber ?? '')
  const [movements, setMovements] = useState<CarrierMovement[]>(
    voyage?.carrierMovements ?? [emptyMovement()]
  )

  const createMutation = useCreateVoyage()
  const updateMutation = useUpdateVoyage(voyage?.voyageNumber ?? '')

  const isPending = createMutation.isPending || updateMutation.isPending

  const handleMovementChange = (index: number, field: keyof CarrierMovement, value: string | number) => {
    setMovements((prev) =>
      prev.map((m, i) => (i === index ? { ...m, [field]: value } : m))
    )
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
    <form onSubmit={handleSubmit}>
      {!isEdit && (
        <div>
          <label htmlFor="voyageNumber">航海番号</label>
          <input
            id="voyageNumber"
            value={voyageNumber}
            onChange={(e) => setVoyageNumber(e.target.value)}
            required
          />
        </div>
      )}

      <fieldset>
        <legend>運航区間</legend>
        {movements.map((m, i) => (
          <div key={i}>
            <input
              aria-label="出発地 UN/LOCODE"
              placeholder="出発地 UN/LOCODE"
              value={m.departureLocationUnlocode}
              onChange={(e) => handleMovementChange(i, 'departureLocationUnlocode', e.target.value)}
              required
            />
            <input
              aria-label="到着地 UN/LOCODE"
              placeholder="到着地 UN/LOCODE"
              value={m.arrivalLocationUnlocode}
              onChange={(e) => handleMovementChange(i, 'arrivalLocationUnlocode', e.target.value)}
              required
            />
            <input
              type="datetime-local"
              aria-label="出発日時"
              value={m.departureDate.slice(0, 16)}
              onChange={(e) => handleMovementChange(i, 'departureDate', e.target.value + ':00+09:00')}
              required
            />
            <input
              type="datetime-local"
              aria-label="到着日時"
              value={m.arrivalDate.slice(0, 16)}
              onChange={(e) => handleMovementChange(i, 'arrivalDate', e.target.value + ':00+09:00')}
              required
            />
            <button type="button" onClick={() => removeMovement(i)}>区間を削除</button>
          </div>
        ))}
        <button type="button" onClick={addMovement}>区間を追加</button>
      </fieldset>

      <button type="submit" disabled={isPending}>
        {isEdit ? '更新' : '登録'}
      </button>
    </form>
  )
}
