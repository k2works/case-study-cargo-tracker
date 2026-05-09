import { useState } from 'react'
import { useCreateBooking } from '../hooks/useBookings'
import type { CargoType, HazmatInfo, TemperatureInfo } from '../types/cargo'

interface BookingFormProps {
  onSuccess?: () => void
  onCancel?: () => void
}

export function BookingForm({ onSuccess, onCancel }: BookingFormProps) {
  const [shipperId, setShipperId] = useState<number>(1)
  const [cargoType, setCargoType] = useState<CargoType>('GENERAL')
  const [weightKg, setWeightKg] = useState<number>(0)
  const [originUnlocode, setOriginUnlocode] = useState('')
  const [destinationUnlocode, setDestinationUnlocode] = useState('')
  const [arrivalDeadline, setArrivalDeadline] = useState('')
  const [hazmatInfo, setHazmatInfo] = useState<HazmatInfo>({ unCode: '', hazardClass: '', packingGroup: '' })
  const [temperatureInfo, setTemperatureInfo] = useState<TemperatureInfo>({ minTemperature: -25, maxTemperature: 5, unit: 'CELSIUS' })

  const createMutation = useCreateBooking()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate(
      {
        shipperId,
        cargoType,
        weightKg,
        originUnlocode,
        destinationUnlocode,
        arrivalDeadline: arrivalDeadline || null,
        hazmatInfo: cargoType === 'HAZARDOUS' ? hazmatInfo : undefined,
        temperatureInfo: cargoType === 'REFRIGERATED' ? temperatureInfo : undefined,
      },
      { onSuccess }
    )
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="shipperId" className="block text-sm font-medium text-gray-700 mb-1">
          荷主 ID
        </label>
        <input
          id="shipperId"
          type="number"
          min={1}
          value={shipperId}
          onChange={(e) => setShipperId(Number(e.target.value))}
          required
          className="border border-gray-300 rounded-md px-3 py-2 text-sm w-32 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
      </div>

      <div>
        <label htmlFor="cargoType" className="block text-sm font-medium text-gray-700 mb-1">
          貨物種別
        </label>
        <select
          id="cargoType"
          value={cargoType}
          onChange={(e) => setCargoType(e.target.value as CargoType)}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm w-48 focus:outline-none focus:ring-1 focus:ring-blue-500"
        >
          <option value="GENERAL">一般貨物</option>
          <option value="REFRIGERATED">冷蔵貨物</option>
          <option value="HAZARDOUS">危険物</option>
          <option value="HEAVY">重量貨物</option>
        </select>
      </div>

      {cargoType === 'HAZARDOUS' && (
        <div className="border border-orange-200 rounded-md p-4 bg-orange-50 space-y-3">
          <p className="text-sm font-medium text-orange-800">危険物申告情報 <span className="text-red-500">*</span></p>
          <div>
            <label htmlFor="hazmatUnCode" className="block text-sm font-medium text-gray-700 mb-1">
              UN コード <span className="text-red-500">*</span>
            </label>
            <input
              id="hazmatUnCode"
              type="text"
              value={hazmatInfo.unCode}
              onChange={(e) => setHazmatInfo((prev) => ({ ...prev, unCode: e.target.value }))}
              required
              placeholder="UN1203"
              className="border border-gray-300 rounded-md px-3 py-2 text-sm w-36 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label htmlFor="hazmatHazardClass" className="block text-sm font-medium text-gray-700 mb-1">
              危険物クラス <span className="text-red-500">*</span>
            </label>
            <input
              id="hazmatHazardClass"
              type="text"
              value={hazmatInfo.hazardClass}
              onChange={(e) => setHazmatInfo((prev) => ({ ...prev, hazardClass: e.target.value }))}
              required
              placeholder="3"
              className="border border-gray-300 rounded-md px-3 py-2 text-sm w-24 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label htmlFor="hazmatPackingGroup" className="block text-sm font-medium text-gray-700 mb-1">
              梱包等級
            </label>
            <input
              id="hazmatPackingGroup"
              type="text"
              value={hazmatInfo.packingGroup}
              onChange={(e) => setHazmatInfo((prev) => ({ ...prev, packingGroup: e.target.value }))}
              placeholder="II"
              className="border border-gray-300 rounded-md px-3 py-2 text-sm w-24 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>
      )}

      {cargoType === 'REFRIGERATED' && (
        <div className="border border-blue-200 rounded-md p-4 bg-blue-50 space-y-3">
          <p className="text-sm font-medium text-blue-800">温度管理条件 <span className="text-red-500">*</span></p>
          <div className="flex gap-4">
            <div>
              <label htmlFor="tempMin" className="block text-sm font-medium text-gray-700 mb-1">
                最低温度 <span className="text-red-500">*</span>
              </label>
              <input
                id="tempMin"
                type="number"
                step={0.1}
                value={temperatureInfo.minTemperature}
                onChange={(e) => setTemperatureInfo((prev) => ({ ...prev, minTemperature: Number(e.target.value) }))}
                required
                className="border border-gray-300 rounded-md px-3 py-2 text-sm w-28 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="tempMax" className="block text-sm font-medium text-gray-700 mb-1">
                最高温度 <span className="text-red-500">*</span>
              </label>
              <input
                id="tempMax"
                type="number"
                step={0.1}
                value={temperatureInfo.maxTemperature}
                onChange={(e) => setTemperatureInfo((prev) => ({ ...prev, maxTemperature: Number(e.target.value) }))}
                required
                className="border border-gray-300 rounded-md px-3 py-2 text-sm w-28 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="tempUnit" className="block text-sm font-medium text-gray-700 mb-1">
                単位 <span className="text-red-500">*</span>
              </label>
              <select
                id="tempUnit"
                value={temperatureInfo.unit}
                onChange={(e) => setTemperatureInfo((prev) => ({ ...prev, unit: e.target.value }))}
                className="border border-gray-300 rounded-md px-3 py-2 text-sm w-32 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="CELSIUS">℃</option>
                <option value="FAHRENHEIT">℉</option>
              </select>
            </div>
          </div>
        </div>
      )}

      <div>
        <label htmlFor="weightKg" className="block text-sm font-medium text-gray-700 mb-1">
          重量 (kg)
        </label>
        <input
          id="weightKg"
          type="number"
          min={0.001}
          step={0.001}
          value={weightKg}
          onChange={(e) => setWeightKg(Number(e.target.value))}
          required
          className="border border-gray-300 rounded-md px-3 py-2 text-sm w-32 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
      </div>

      <div className="flex gap-4">
        <div>
          <label htmlFor="originUnlocode" className="block text-sm font-medium text-gray-700 mb-1">
            出発地 (UNLOCODE)
          </label>
          <input
            id="originUnlocode"
            value={originUnlocode}
            onChange={(e) => setOriginUnlocode(e.target.value.toUpperCase())}
            required
            placeholder="JPTYO"
            maxLength={5}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm w-28 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="destinationUnlocode" className="block text-sm font-medium text-gray-700 mb-1">
            到着地 (UNLOCODE)
          </label>
          <input
            id="destinationUnlocode"
            value={destinationUnlocode}
            onChange={(e) => setDestinationUnlocode(e.target.value.toUpperCase())}
            required
            placeholder="CNSHA"
            maxLength={5}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm w-28 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
      </div>

      <div>
        <label htmlFor="arrivalDeadline" className="block text-sm font-medium text-gray-700 mb-1">
          到着期限
        </label>
        <input
          id="arrivalDeadline"
          type="date"
          value={arrivalDeadline}
          onChange={(e) => setArrivalDeadline(e.target.value)}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
      </div>

      <div className="flex gap-3 pt-2">
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          登録
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

      {createMutation.isError && (
        <p className="text-sm text-red-600">登録に失敗しました。入力内容を確認してください。</p>
      )}
    </form>
  )
}
