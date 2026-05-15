import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useBookCargo } from '../hooks/useBookings'
import { CargoTypeFields } from './CargoTypeFields'
import type { BookCargoRequest, CargoType, HazardInfo, TemperatureCondition } from '../types/booking'

interface FormState {
  shipperId: string
  cargoType: CargoType
  weightKg: string
  quantity: string
  productName: string
  lengthCm: string
  widthCm: string
  heightCm: string
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  hazardInfo?: HazardInfo
  temperatureCondition?: TemperatureCondition
}

const INITIAL_STATE: FormState = {
  shipperId: '',
  cargoType: 'GENERAL',
  weightKg: '',
  quantity: '1',
  productName: '',
  lengthCm: '',
  widthCm: '',
  heightCm: '',
  originUnLocode: '',
  destinationUnLocode: '',
  arrivalDeadline: '',
}

export function BookingForm() {
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState>(INITIAL_STATE)
  const book = useBookCargo()

  const handleSubmit = (e: { preventDefault(): void }) => {
    e.preventDefault()
    const request: BookCargoRequest = {
      shipperId: Number(form.shipperId),
      cargoSpec: {
        cargoType: form.cargoType,
        weightKg: Number(form.weightKg),
        quantity: Number(form.quantity),
        productName: form.productName,
        dimensions: {
          lengthCm: Number(form.lengthCm),
          widthCm: Number(form.widthCm),
          heightCm: Number(form.heightCm),
        },
        ...(form.cargoType === 'HAZARDOUS' && form.hazardInfo
          ? { hazardInfo: form.hazardInfo }
          : {}),
        ...(form.cargoType === 'REFRIGERATED' && form.temperatureCondition
          ? { temperatureCondition: form.temperatureCondition }
          : {}),
      },
      routeSpec: {
        originUnLocode: form.originUnLocode,
        destinationUnLocode: form.destinationUnLocode,
        arrivalDeadline: form.arrivalDeadline,
      },
    }
    book.mutate(request, {
      onSuccess: () => {
        navigate('/bookings')
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="booking-shipper-id" className="block text-sm font-medium text-gray-700">
          荷主 ID
        </label>
        <input
          id="booking-shipper-id"
          type="number"
          value={form.shipperId}
          onChange={(e) => setForm({ ...form, shipperId: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label htmlFor="booking-cargo-type" className="block text-sm font-medium text-gray-700">
          貨物種別
        </label>
        <select
          id="booking-cargo-type"
          value={form.cargoType}
          onChange={(e) => setForm({ ...form, cargoType: e.target.value as CargoType })}
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        >
          <option value="GENERAL">一般貨物</option>
          <option value="HAZARDOUS">危険物</option>
          <option value="REFRIGERATED">冷凍・冷蔵</option>
        </select>
      </div>
      <div>
        <label htmlFor="booking-weight" className="block text-sm font-medium text-gray-700">
          重量（kg）
        </label>
        <input
          id="booking-weight"
          type="number"
          value={form.weightKg}
          onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
          required
          step="0.01"
          min="0"
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label htmlFor="booking-quantity" className="block text-sm font-medium text-gray-700">
          個数
        </label>
        <input
          id="booking-quantity"
          type="number"
          value={form.quantity}
          onChange={(e) => setForm({ ...form, quantity: e.target.value })}
          required
          min="1"
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label htmlFor="booking-product-name" className="block text-sm font-medium text-gray-700">
          品名
        </label>
        <input
          id="booking-product-name"
          type="text"
          value={form.productName}
          onChange={(e) => setForm({ ...form, productName: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>
      <div className="flex gap-4">
        <div className="flex-1">
          <label htmlFor="booking-length" className="block text-sm font-medium text-gray-700">
            長さ（cm）
          </label>
          <input
            id="booking-length"
            type="number"
            value={form.lengthCm}
            onChange={(e) => setForm({ ...form, lengthCm: e.target.value })}
            required
            min="0"
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
        <div className="flex-1">
          <label htmlFor="booking-width" className="block text-sm font-medium text-gray-700">
            幅（cm）
          </label>
          <input
            id="booking-width"
            type="number"
            value={form.widthCm}
            onChange={(e) => setForm({ ...form, widthCm: e.target.value })}
            required
            min="0"
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
        <div className="flex-1">
          <label htmlFor="booking-height" className="block text-sm font-medium text-gray-700">
            高さ（cm）
          </label>
          <input
            id="booking-height"
            type="number"
            value={form.heightCm}
            onChange={(e) => setForm({ ...form, heightCm: e.target.value })}
            required
            min="0"
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
      </div>
      <CargoTypeFields
        cargoType={form.cargoType}
        hazardInfo={form.hazardInfo}
        temperatureCondition={form.temperatureCondition}
        onHazardChange={(hazardInfo) => setForm({ ...form, hazardInfo })}
        onTemperatureChange={(temperatureCondition) => setForm({ ...form, temperatureCondition })}
      />
      <div>
        <label htmlFor="booking-origin" className="block text-sm font-medium text-gray-700">
          出発地（UN/LOCODE）
        </label>
        <input
          id="booking-origin"
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
      <div>
        <label htmlFor="booking-destination" className="block text-sm font-medium text-gray-700">
          目的地（UN/LOCODE）
        </label>
        <input
          id="booking-destination"
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
      <div>
        <label htmlFor="booking-deadline" className="block text-sm font-medium text-gray-700">
          到着期限
        </label>
        <input
          id="booking-deadline"
          type="date"
          value={form.arrivalDeadline}
          onChange={(e) => setForm({ ...form, arrivalDeadline: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>
      <div className="flex gap-3">
        <button
          type="submit"
          disabled={book.isPending}
          className="flex-1 bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700 disabled:opacity-50"
        >
          {book.isPending ? '登録中...' : '登録する'}
        </button>
        <button
          type="button"
          onClick={() => navigate('/bookings')}
          className="flex-1 bg-gray-100 text-gray-700 py-2 px-4 rounded-md text-sm hover:bg-gray-200"
        >
          キャンセル
        </button>
      </div>
      {book.isError && <p className="text-red-600 text-sm">登録に失敗しました。</p>}
    </form>
  )
}
