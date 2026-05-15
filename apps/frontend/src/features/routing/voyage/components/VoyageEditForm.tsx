import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useUpdateVoyage } from '../hooks/useVoyages'
import type { CargoType, Movement, UpdateVoyageScheduleRequest, VoyageListItem } from '../types/voyage'

// US25: 既存航海スケジュール更新フォーム（S12 / `:vn/edit`）。
// voyageNumber / carrier / shipName / origin / destination は更新対象外なので read-only。
// departureDate / arrivalDate / movements / acceptedCargoTypes のみ編集可。
// 各フィールドの変更前後の値を比較し、差分があるフィールドに `✎ 変更` バッジを表示する。

type FormMovement = Movement & { _id: string }

interface FormState {
  departureDate: string
  arrivalDate: string
  movements: FormMovement[]
  acceptedCargoTypes: Set<CargoType>
}

const newMovementId = (): string => crypto.randomUUID()

const CARGO_TYPES: Array<{ value: CargoType; label: string }> = [
  { value: 'GENERAL', label: '一般貨物' },
  { value: 'HAZARDOUS', label: '危険物' },
  { value: 'REFRIGERATED', label: '冷凍・冷蔵' },
]

interface Props {
  current: VoyageListItem
  // 既存の carrier_movement と acceptedCargoTypes は GET /api/v1/voyages/{vn} の現状レスポンスに
  // 含まれていないため、現時点では編集開始時の初期値として空配列を渡す。
  // 完全な差分表示は GET 側の拡張（IT4 以降）で対応。
  initialMovements?: Movement[]
  initialCargoTypes?: CargoType[]
}

export function VoyageEditForm({
  current,
  initialMovements = [],
  initialCargoTypes = ['GENERAL'],
}: Props) {
  const navigate = useNavigate()
  const update = useUpdateVoyage(current.voyageNumber)

  const [form, setForm] = useState<FormState>({
    departureDate: current.departureDate,
    arrivalDate: current.arrivalDate,
    movements:
      initialMovements.length > 0
        ? initialMovements.map((m) => ({ ...m, _id: newMovementId() }))
        : [
            {
              _id: newMovementId(),
              departureUnLocode: current.originUnLocode,
              arrivalUnLocode: current.destinationUnLocode,
              departureTime: current.departureDate,
              arrivalTime: current.arrivalDate,
            },
          ],
    acceptedCargoTypes: new Set<CargoType>(initialCargoTypes),
  })

  const isDepartureChanged = form.departureDate !== current.departureDate
  const isArrivalChanged = form.arrivalDate !== current.arrivalDate

  const updateMovement = (index: number, patch: Partial<Movement>) => {
    setForm({
      ...form,
      movements: form.movements.map((m, i) => (i === index ? { ...m, ...patch } : m)),
    })
  }

  const addMovement = () => {
    setForm({
      ...form,
      movements: [
        ...form.movements,
        {
          _id: newMovementId(),
          departureUnLocode: '',
          arrivalUnLocode: '',
          departureTime: '',
          arrivalTime: '',
        },
      ],
    })
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
    const request: UpdateVoyageScheduleRequest = {
      departureDate: form.departureDate,
      arrivalDate: form.arrivalDate,
      carrierMovements: form.movements.map((m) => ({
        departureUnLocode: m.departureUnLocode,
        arrivalUnLocode: m.arrivalUnLocode,
        departureTime: m.departureTime,
        arrivalTime: m.arrivalTime,
      })),
      acceptedCargoTypes: Array.from(form.acceptedCargoTypes),
    }
    update.mutate(request, {
      onSuccess: () => {
        navigate('/routing/voyages')
      },
    })
  }

  const handleCancel = () => {
    // US25 受入条件 5: キャンセル時は既存スケジュールを変更せず一覧に戻る
    navigate('/routing/voyages')
  }

  const changeBadge = (changed: boolean) =>
    changed ? (
      <span
        className="ml-2 inline-block rounded bg-yellow-100 px-1.5 py-0.5 text-xs font-medium text-yellow-800"
        data-testid="change-badge"
      >
        ✎ 変更
      </span>
    ) : null

  return (
    <form onSubmit={handleSubmit} className="space-y-6" data-testid="voyage-edit-form">
      <div className="rounded border border-gray-200 bg-gray-50 p-4 text-sm text-gray-700">
        <p>
          航海番号 <strong>{current.voyageNumber}</strong> の運送会社・船名・出発港・到着港は
          変更できません。スケジュール（日時・寄港地・対応貨物種別）のみ編集できます。
        </p>
      </div>

      <fieldset className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm text-gray-600">運送会社</label>
          <input value={current.carrierName} readOnly className="w-full rounded border bg-gray-100 px-3 py-2" />
        </div>
        <div>
          <label className="block text-sm text-gray-600">船名</label>
          <input value={current.shipName} readOnly className="w-full rounded border bg-gray-100 px-3 py-2" />
        </div>
        <div>
          <label className="block text-sm text-gray-600">出発港</label>
          <input value={current.originUnLocode} readOnly className="w-full rounded border bg-gray-100 px-3 py-2" />
        </div>
        <div>
          <label className="block text-sm text-gray-600">到着港</label>
          <input value={current.destinationUnLocode} readOnly className="w-full rounded border bg-gray-100 px-3 py-2" />
        </div>
      </fieldset>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700">
            出発日時 {changeBadge(isDepartureChanged)}
          </label>
          <input
            type="datetime-local"
            value={form.departureDate}
            onChange={(e) => setForm({ ...form, departureDate: e.target.value })}
            className="w-full rounded border border-gray-300 px-3 py-2"
            required
            data-testid="departure-date"
          />
          {isDepartureChanged && (
            <p className="mt-1 text-xs text-gray-500">変更前: {current.departureDate}</p>
          )}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            到着日時 {changeBadge(isArrivalChanged)}
          </label>
          <input
            type="datetime-local"
            value={form.arrivalDate}
            onChange={(e) => setForm({ ...form, arrivalDate: e.target.value })}
            className="w-full rounded border border-gray-300 px-3 py-2"
            required
            data-testid="arrival-date"
          />
          {isArrivalChanged && (
            <p className="mt-1 text-xs text-gray-500">変更前: {current.arrivalDate}</p>
          )}
        </div>
      </div>

      <fieldset className="space-y-3">
        <legend className="text-sm font-medium text-gray-700">寄港地（編集可）</legend>
        {form.movements.map((m, i) => (
          <div key={m._id} className="grid grid-cols-5 gap-2 rounded border border-gray-200 p-3">
            <input
              placeholder="出発港"
              value={m.departureUnLocode}
              onChange={(e) => updateMovement(i, { departureUnLocode: e.target.value })}
              className="rounded border px-2 py-1"
              required
            />
            <input
              placeholder="到着港"
              value={m.arrivalUnLocode}
              onChange={(e) => updateMovement(i, { arrivalUnLocode: e.target.value })}
              className="rounded border px-2 py-1"
              required
            />
            <input
              type="datetime-local"
              value={m.departureTime}
              onChange={(e) => updateMovement(i, { departureTime: e.target.value })}
              className="rounded border px-2 py-1"
              required
            />
            <input
              type="datetime-local"
              value={m.arrivalTime}
              onChange={(e) => updateMovement(i, { arrivalTime: e.target.value })}
              className="rounded border px-2 py-1"
              required
            />
            <button
              type="button"
              onClick={() => removeMovement(i)}
              disabled={form.movements.length === 1}
              className="rounded border border-red-300 px-2 py-1 text-red-700 disabled:opacity-50"
            >
              削除
            </button>
          </div>
        ))}
        <button type="button" onClick={addMovement} className="text-sm text-blue-600 underline">
          + 寄港地を追加
        </button>
      </fieldset>

      <fieldset>
        <legend className="text-sm font-medium text-gray-700">対応貨物種別（編集可）</legend>
        <div className="mt-2 flex gap-4">
          {CARGO_TYPES.map((t) => (
            <label key={t.value} className="inline-flex items-center gap-2">
              <input
                type="checkbox"
                checked={form.acceptedCargoTypes.has(t.value)}
                onChange={() => toggleCargoType(t.value)}
              />
              <span>{t.label}</span>
            </label>
          ))}
        </div>
      </fieldset>

      {update.isError && (
        <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" role="alert">
          更新に失敗しました: {update.error instanceof Error ? update.error.message : '不明なエラー'}
        </div>
      )}

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={update.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {update.isPending ? '更新中…' : '更新する'}
        </button>
        <button
          type="button"
          onClick={handleCancel}
          className="rounded border border-gray-300 px-4 py-2 text-gray-700"
        >
          キャンセル
        </button>
      </div>
    </form>
  )
}
