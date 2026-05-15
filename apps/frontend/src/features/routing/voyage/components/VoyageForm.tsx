import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useRegisterVoyage } from '../hooks/useVoyages'
import type { CargoType, Movement, RegisterVoyageRequest } from '../types/voyage'

// React の key として配列 index を使うと並び替え時に再マウントが正しく行えないため、
// 各 movement に UI ローカルの安定 ID を付与する（API には送らない）。
type FormMovement = Movement & { _id: string }

interface FormState {
  voyageNumber: string
  carrierCode: string
  carrierName: string
  shipName: string
  originUnLocode: string
  destinationUnLocode: string
  departureDate: string
  arrivalDate: string
  movements: FormMovement[]
  acceptedCargoTypes: Set<CargoType>
}

// UI ローカル用の安定 ID。crypto.randomUUID() は Node 19+ / 全モダンブラウザで標準。
const newMovementId = (): string => crypto.randomUUID()

const createEmptyMovement = (): FormMovement => ({
  _id: newMovementId(),
  departureUnLocode: '',
  arrivalUnLocode: '',
  departureTime: '',
  arrivalTime: '',
})

const INITIAL_STATE: FormState = {
  voyageNumber: '',
  carrierCode: '',
  carrierName: '',
  shipName: '',
  originUnLocode: '',
  destinationUnLocode: '',
  departureDate: '',
  arrivalDate: '',
  movements: [createEmptyMovement()],
  acceptedCargoTypes: new Set<CargoType>(['GENERAL']),
}

const CARGO_TYPES: Array<{ value: CargoType; label: string }> = [
  { value: 'GENERAL', label: '一般貨物' },
  { value: 'HAZARDOUS', label: '危険物' },
  { value: 'REFRIGERATED', label: '冷凍・冷蔵' },
]

export function VoyageForm() {
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState>(INITIAL_STATE)
  const register = useRegisterVoyage()

  const updateMovement = (index: number, patch: Partial<Movement>) => {
    setForm({
      ...form,
      movements: form.movements.map((m, i) => (i === index ? { ...m, ...patch } : m)),
    })
  }

  const addMovement = () => {
    setForm({ ...form, movements: [...form.movements, createEmptyMovement()] })
  }

  const removeMovement = (index: number) => {
    if (form.movements.length === 1) return
    setForm({ ...form, movements: form.movements.filter((_, i) => i !== index) })
  }

  const toggleCargoType = (type: CargoType) => {
    const next = new Set(form.acceptedCargoTypes)
    if (next.has(type)) {
      next.delete(type)
    } else {
      next.add(type)
    }
    setForm({ ...form, acceptedCargoTypes: next })
  }

  const handleSubmit = (e: { preventDefault(): void }) => {
    e.preventDefault()
    const request: RegisterVoyageRequest = {
      voyageNumber: form.voyageNumber,
      carrierCode: form.carrierCode,
      carrierName: form.carrierName,
      shipName: form.shipName,
      originUnLocode: form.originUnLocode,
      destinationUnLocode: form.destinationUnLocode,
      departureDate: form.departureDate,
      arrivalDate: form.arrivalDate,
      // UI ローカルの _id は API へ送らないため除外
      carrierMovements: form.movements.map((m): Movement => ({
        departureUnLocode: m.departureUnLocode,
        arrivalUnLocode: m.arrivalUnLocode,
        departureTime: m.departureTime,
        arrivalTime: m.arrivalTime,
      })),
      acceptedCargoTypes: Array.from(form.acceptedCargoTypes),
    }
    register.mutate(request, {
      onSuccess: () => {
        navigate('/routing/voyages')
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="voyage-number" className="block text-sm font-medium text-gray-700">
          航海番号
        </label>
        <input
          id="voyage-number"
          type="text"
          value={form.voyageNumber}
          onChange={(e) => setForm({ ...form, voyageNumber: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          placeholder="V12345"
        />
      </div>
      <div>
        <label htmlFor="voyage-ship-name" className="block text-sm font-medium text-gray-700">
          船名
        </label>
        <input
          id="voyage-ship-name"
          type="text"
          value={form.shipName}
          onChange={(e) => setForm({ ...form, shipName: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>
      <div className="flex gap-4">
        <div className="flex-1">
          <label htmlFor="voyage-carrier-code" className="block text-sm font-medium text-gray-700">
            運送会社コード
          </label>
          <input
            id="voyage-carrier-code"
            type="text"
            value={form.carrierCode}
            onChange={(e) => setForm({ ...form, carrierCode: e.target.value.toUpperCase() })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm uppercase"
          />
        </div>
        <div className="flex-1">
          <label htmlFor="voyage-carrier-name" className="block text-sm font-medium text-gray-700">
            運送会社名
          </label>
          <input
            id="voyage-carrier-name"
            type="text"
            value={form.carrierName}
            onChange={(e) => setForm({ ...form, carrierName: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
      </div>
      <div className="flex gap-4">
        <div className="flex-1">
          <label htmlFor="voyage-origin" className="block text-sm font-medium text-gray-700">
            出発港（UN/LOCODE）
          </label>
          <input
            id="voyage-origin"
            type="text"
            value={form.originUnLocode}
            onChange={(e) =>
              setForm({ ...form, originUnLocode: e.target.value.toUpperCase() })
            }
            required
            pattern="[A-Z]{5}"
            maxLength={5}
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm uppercase"
            placeholder="JPYOK"
          />
        </div>
        <div className="flex-1">
          <label htmlFor="voyage-destination" className="block text-sm font-medium text-gray-700">
            到着港（UN/LOCODE）
          </label>
          <input
            id="voyage-destination"
            type="text"
            value={form.destinationUnLocode}
            onChange={(e) =>
              setForm({ ...form, destinationUnLocode: e.target.value.toUpperCase() })
            }
            required
            pattern="[A-Z]{5}"
            maxLength={5}
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm uppercase"
            placeholder="USLAX"
          />
        </div>
      </div>
      <div className="flex gap-4">
        <div className="flex-1">
          <label htmlFor="voyage-departure" className="block text-sm font-medium text-gray-700">
            出発日時
          </label>
          <input
            id="voyage-departure"
            type="datetime-local"
            value={form.departureDate}
            onChange={(e) => setForm({ ...form, departureDate: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
        <div className="flex-1">
          <label htmlFor="voyage-arrival" className="block text-sm font-medium text-gray-700">
            到着日時
          </label>
          <input
            id="voyage-arrival"
            type="datetime-local"
            value={form.arrivalDate}
            onChange={(e) => setForm({ ...form, arrivalDate: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
      </div>
      <fieldset className="space-y-2 border border-gray-200 rounded-md p-3">
        <legend className="text-sm font-medium text-gray-700 px-1">対応貨物種別</legend>
        <div className="flex gap-4 flex-wrap">
          {CARGO_TYPES.map((type) => (
            <label key={type.value} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.acceptedCargoTypes.has(type.value)}
                onChange={() => toggleCargoType(type.value)}
              />
              {type.label}
            </label>
          ))}
        </div>
      </fieldset>
      <fieldset className="space-y-3 border border-gray-200 rounded-md p-3">
        <legend className="text-sm font-medium text-gray-700 px-1">寄港地</legend>
        {form.movements.map((m, index) => (
          <div key={m._id} className="space-y-2 border-b border-gray-100 pb-3 last:border-b-0">
            <div className="flex gap-2">
              <div className="flex-1">
                <label
                  htmlFor={`movement-${index}-from`}
                  className="block text-xs font-medium text-gray-600"
                >
                  {`寄港地 ${index + 1} 出発港`}
                </label>
                <input
                  id={`movement-${index}-from`}
                  type="text"
                  value={m.departureUnLocode}
                  onChange={(e) =>
                    updateMovement(index, { departureUnLocode: e.target.value.toUpperCase() })
                  }
                  required
                  pattern="[A-Z]{5}"
                  maxLength={5}
                  className="mt-1 block w-full border border-gray-300 rounded-md px-2 py-1 text-sm uppercase"
                />
              </div>
              <div className="flex-1">
                <label
                  htmlFor={`movement-${index}-to`}
                  className="block text-xs font-medium text-gray-600"
                >
                  {`寄港地 ${index + 1} 到着港`}
                </label>
                <input
                  id={`movement-${index}-to`}
                  type="text"
                  value={m.arrivalUnLocode}
                  onChange={(e) =>
                    updateMovement(index, { arrivalUnLocode: e.target.value.toUpperCase() })
                  }
                  required
                  pattern="[A-Z]{5}"
                  maxLength={5}
                  className="mt-1 block w-full border border-gray-300 rounded-md px-2 py-1 text-sm uppercase"
                />
              </div>
            </div>
            <div className="flex gap-2">
              <div className="flex-1">
                <label
                  htmlFor={`movement-${index}-dep-time`}
                  className="block text-xs font-medium text-gray-600"
                >
                  出発時刻
                </label>
                <input
                  id={`movement-${index}-dep-time`}
                  type="datetime-local"
                  value={m.departureTime}
                  onChange={(e) => updateMovement(index, { departureTime: e.target.value })}
                  required
                  className="mt-1 block w-full border border-gray-300 rounded-md px-2 py-1 text-sm"
                />
              </div>
              <div className="flex-1">
                <label
                  htmlFor={`movement-${index}-arr-time`}
                  className="block text-xs font-medium text-gray-600"
                >
                  到着時刻
                </label>
                <input
                  id={`movement-${index}-arr-time`}
                  type="datetime-local"
                  value={m.arrivalTime}
                  onChange={(e) => updateMovement(index, { arrivalTime: e.target.value })}
                  required
                  className="mt-1 block w-full border border-gray-300 rounded-md px-2 py-1 text-sm"
                />
              </div>
              <button
                type="button"
                onClick={() => removeMovement(index)}
                disabled={form.movements.length === 1}
                className="mt-5 text-xs text-red-600 hover:text-red-800 disabled:text-gray-300"
              >
                削除
              </button>
            </div>
          </div>
        ))}
        <button
          type="button"
          onClick={addMovement}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          寄港地を追加
        </button>
      </fieldset>
      <div className="flex gap-3">
        <button
          type="submit"
          disabled={register.isPending}
          className="flex-1 bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700 disabled:opacity-50"
        >
          {register.isPending ? '登録中...' : '登録する'}
        </button>
        <button
          type="button"
          onClick={() => navigate('/routing/voyages')}
          className="flex-1 bg-gray-100 text-gray-700 py-2 px-4 rounded-md text-sm hover:bg-gray-200"
        >
          キャンセル
        </button>
      </div>
      {register.isError && <p className="text-red-600 text-sm">登録に失敗しました。</p>}
    </form>
  )
}
