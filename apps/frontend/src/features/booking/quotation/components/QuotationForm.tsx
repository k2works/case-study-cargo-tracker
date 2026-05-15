import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useCreateQuotation } from '../hooks/useQuotations'
import type { CargoType, CreateQuotationRequest, HazardInfoDto } from '../types/quotation'

// S03 見積作成（US01）。
// 受入条件:
// 1. 出発地・目的地・希望期限・貨物種別・重量を入力できる
// 6. 危険物が含まれる場合、危険物申告情報の入力フォームが表示される

interface FormState {
  shipperId: string
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  cargoType: CargoType
  weightKg: string
  imoClass: string
  unNumber: string
  declaration: string
}

const INITIAL_STATE: FormState = {
  shipperId: '',
  originUnLocode: '',
  destinationUnLocode: '',
  arrivalDeadline: '',
  cargoType: 'GENERAL',
  weightKg: '',
  imoClass: '',
  unNumber: '',
  declaration: '',
}

const CARGO_TYPES: Array<{ value: CargoType; label: string }> = [
  { value: 'GENERAL', label: '一般貨物' },
  { value: 'HAZARDOUS', label: '危険物' },
  { value: 'REFRIGERATED', label: '冷凍・冷蔵' },
]

export function QuotationForm() {
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState>(INITIAL_STATE)
  const create = useCreateQuotation()

  const isHazardous = form.cargoType === 'HAZARDOUS'

  const handleSubmit = (e: { preventDefault(): void }) => {
    e.preventDefault()

    let hazardInfo: HazardInfoDto | null = null
    if (isHazardous) {
      hazardInfo = {
        imoClass: form.imoClass,
        unNumber: form.unNumber,
        declaration: form.declaration,
      }
    }

    const request: CreateQuotationRequest = {
      shipperId: Number(form.shipperId),
      originUnLocode: form.originUnLocode,
      destinationUnLocode: form.destinationUnLocode,
      arrivalDeadline: form.arrivalDeadline,
      cargoType: form.cargoType,
      weightKg: Number(form.weightKg),
      hazardInfo,
    }

    create.mutate(request, {
      onSuccess: (response) => {
        navigate(`/quotations/${response.quotationId}`)
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" data-testid="quotation-form">
      <div>
        <label htmlFor="shipperId" className="block text-sm font-medium text-gray-700">
          荷主 ID
        </label>
        <input
          id="shipperId"
          type="number"
          value={form.shipperId}
          onChange={(e) => setForm({ ...form, shipperId: e.target.value })}
          className="w-full rounded border border-gray-300 px-3 py-2"
          required
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="originUnLocode" className="block text-sm font-medium text-gray-700">
            出発地（UN/LOCODE）
          </label>
          <input
            id="originUnLocode"
            type="text"
            value={form.originUnLocode}
            onChange={(e) => setForm({ ...form, originUnLocode: e.target.value.toUpperCase() })}
            placeholder="JPTYO"
            maxLength={5}
            className="w-full rounded border border-gray-300 px-3 py-2"
            required
          />
        </div>
        <div>
          <label htmlFor="destinationUnLocode" className="block text-sm font-medium text-gray-700">
            目的地（UN/LOCODE）
          </label>
          <input
            id="destinationUnLocode"
            type="text"
            value={form.destinationUnLocode}
            onChange={(e) =>
              setForm({ ...form, destinationUnLocode: e.target.value.toUpperCase() })
            }
            placeholder="USNYC"
            maxLength={5}
            className="w-full rounded border border-gray-300 px-3 py-2"
            required
          />
        </div>
      </div>

      <div>
        <label htmlFor="arrivalDeadline" className="block text-sm font-medium text-gray-700">
          希望期限
        </label>
        <input
          id="arrivalDeadline"
          type="date"
          value={form.arrivalDeadline}
          onChange={(e) => setForm({ ...form, arrivalDeadline: e.target.value })}
          className="w-full rounded border border-gray-300 px-3 py-2"
          required
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="cargoType" className="block text-sm font-medium text-gray-700">
            貨物種別
          </label>
          <select
            id="cargoType"
            value={form.cargoType}
            onChange={(e) => setForm({ ...form, cargoType: e.target.value as CargoType })}
            className="w-full rounded border border-gray-300 px-3 py-2"
          >
            {CARGO_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="weightKg" className="block text-sm font-medium text-gray-700">
            重量 (kg)
          </label>
          <input
            id="weightKg"
            type="number"
            min="0.01"
            step="0.01"
            value={form.weightKg}
            onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
            className="w-full rounded border border-gray-300 px-3 py-2"
            required
          />
        </div>
      </div>

      {isHazardous && (
        <fieldset className="rounded border border-yellow-200 bg-yellow-50 p-4" data-testid="hazard-info-fieldset">
          <legend className="px-1 text-sm font-medium text-yellow-900">
            危険物申告（受入条件 6）
          </legend>
          <div className="mt-2 grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="imoClass" className="block text-sm text-gray-700">
                IMO クラス
              </label>
              <input
                id="imoClass"
                type="text"
                value={form.imoClass}
                onChange={(e) => setForm({ ...form, imoClass: e.target.value })}
                placeholder="Class 3"
                className="w-full rounded border border-gray-300 px-3 py-2"
                required
              />
            </div>
            <div>
              <label htmlFor="unNumber" className="block text-sm text-gray-700">
                UN 番号
              </label>
              <input
                id="unNumber"
                type="text"
                value={form.unNumber}
                onChange={(e) => setForm({ ...form, unNumber: e.target.value })}
                placeholder="UN1170"
                className="w-full rounded border border-gray-300 px-3 py-2"
                required
              />
            </div>
          </div>
          <div className="mt-3">
            <label htmlFor="declaration" className="block text-sm text-gray-700">
              申告内容
            </label>
            <textarea
              id="declaration"
              value={form.declaration}
              onChange={(e) => setForm({ ...form, declaration: e.target.value })}
              className="w-full rounded border border-gray-300 px-3 py-2"
              rows={2}
              required
            />
          </div>
        </fieldset>
      )}

      {create.isError && (
        <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" role="alert">
          見積作成に失敗しました: {create.error instanceof Error ? create.error.message : '不明なエラー'}
        </div>
      )}

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={create.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {create.isPending ? '作成中…' : '見積を作成'}
        </button>
        <button
          type="button"
          onClick={() => navigate('/shippers')}
          className="rounded border border-gray-300 px-4 py-2 text-gray-700"
        >
          キャンセル
        </button>
      </div>
    </form>
  )
}
