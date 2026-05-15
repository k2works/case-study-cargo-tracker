import type { CargoType, HazardInfo, TemperatureCondition } from '../types/booking'

interface CargoTypeFieldsProps {
  readonly cargoType: CargoType
  readonly hazardInfo?: HazardInfo
  readonly temperatureCondition?: TemperatureCondition
  readonly onHazardChange: (value: HazardInfo) => void
  readonly onTemperatureChange: (value: TemperatureCondition) => void
}

const EMPTY_HAZARD: HazardInfo = { imoClass: '', unNumber: '', declaration: '' }
const EMPTY_TEMP: TemperatureCondition = { minCelsius: 0, maxCelsius: 0 }

export function CargoTypeFields({
  cargoType,
  hazardInfo,
  temperatureCondition,
  onHazardChange,
  onTemperatureChange,
}: CargoTypeFieldsProps) {
  if (cargoType === 'HAZARDOUS') {
    const value = hazardInfo ?? EMPTY_HAZARD
    return (
      <fieldset className="space-y-4 border border-gray-200 rounded-md p-3">
        <legend className="text-sm font-medium text-gray-700 px-1">危険物情報</legend>
        <div>
          <label htmlFor="hazard-imo" className="block text-sm font-medium text-gray-700">
            IMO クラス
          </label>
          <input
            id="hazard-imo"
            type="text"
            value={value.imoClass}
            onChange={(e) => onHazardChange({ ...value, imoClass: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label htmlFor="hazard-un" className="block text-sm font-medium text-gray-700">
            UN 番号
          </label>
          <input
            id="hazard-un"
            type="text"
            value={value.unNumber}
            onChange={(e) => onHazardChange({ ...value, unNumber: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label htmlFor="hazard-declaration" className="block text-sm font-medium text-gray-700">
            危険物宣言
          </label>
          <input
            id="hazard-declaration"
            type="text"
            value={value.declaration}
            onChange={(e) => onHazardChange({ ...value, declaration: e.target.value })}
            required
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          />
        </div>
      </fieldset>
    )
  }

  if (cargoType === 'REFRIGERATED') {
    const value = temperatureCondition ?? EMPTY_TEMP
    return (
      <fieldset className="space-y-4 border border-gray-200 rounded-md p-3">
        <legend className="text-sm font-medium text-gray-700 px-1">温度条件</legend>
        <div className="flex gap-4">
          <div className="flex-1">
            <label htmlFor="temp-min" className="block text-sm font-medium text-gray-700">
              最低温度（℃）
            </label>
            <input
              id="temp-min"
              type="number"
              value={value.minCelsius}
              onChange={(e) =>
                onTemperatureChange({ ...value, minCelsius: Number(e.target.value) })
              }
              required
              step="0.1"
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div className="flex-1">
            <label htmlFor="temp-max" className="block text-sm font-medium text-gray-700">
              最高温度（℃）
            </label>
            <input
              id="temp-max"
              type="number"
              value={value.maxCelsius}
              onChange={(e) =>
                onTemperatureChange({ ...value, maxCelsius: Number(e.target.value) })
              }
              required
              step="0.1"
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
        </div>
      </fieldset>
    )
  }

  return null
}
