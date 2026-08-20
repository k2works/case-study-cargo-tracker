import type { HazardousInput, TemperatureInput } from './booking-form-types'

/**
 * 貨物種別で出入りする追加項目（US05）。
 *
 * 一般貨物に危険物申告や温度条件を付けられないのは業務の規則であり、画面もそれに従う。
 * 種別を戻したときに入力値を捨てるのは、この部品を出し入れする側（画面）の責務にしている。
 */

export function HazardousFields({
  value,
  onChange,
}: Readonly<{
  value: HazardousInput
  onChange: (next: HazardousInput) => void
}>) {
  return (
    <fieldset className="space-y-4 rounded border border-red-200 bg-red-50 p-4">
      <legend className="px-1 text-sm font-medium text-gray-700">危険物申告</legend>

      <div>
        <label htmlFor="hazardousClass" className="block text-sm font-medium text-gray-700">
          危険物クラス
        </label>
        <input
          id="hazardousClass"
          type="text"
          value={value.hazardousClass}
          onChange={(event) => onChange({ ...value, hazardousClass: event.target.value })}
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>

      <div>
        <label htmlFor="unNumber" className="block text-sm font-medium text-gray-700">
          UN 番号
        </label>
        <input
          id="unNumber"
          type="text"
          value={value.unNumber}
          onChange={(event) => onChange({ ...value, unNumber: event.target.value })}
          placeholder="UN1263"
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>

      <div>
        <label htmlFor="properShippingName" className="block text-sm font-medium text-gray-700">
          正式品名
        </label>
        <input
          id="properShippingName"
          type="text"
          value={value.properShippingName}
          onChange={(event) => onChange({ ...value, properShippingName: event.target.value })}
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>

      <p className="text-sm text-gray-600">
        3 項目そろって初めて法的要件を満たします。どれか 1 つでも欠けた申告は使えません。
      </p>
    </fieldset>
  )
}

export function TemperatureFields({
  value,
  onChange,
}: Readonly<{
  value: TemperatureInput
  onChange: (next: TemperatureInput) => void
}>) {
  return (
    <fieldset className="space-y-4 rounded border border-sky-200 bg-sky-50 p-4">
      <legend className="px-1 text-sm font-medium text-gray-700">温度管理条件</legend>

      <div className="flex gap-4">
        <div className="flex-1">
          <label htmlFor="minCelsius" className="block text-sm font-medium text-gray-700">
            保管温度の下限（℃）
          </label>
          <input
            id="minCelsius"
            type="number"
            step="any"
            value={value.minCelsius}
            onChange={(event) => onChange({ ...value, minCelsius: event.target.value })}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div className="flex-1">
          <label htmlFor="maxCelsius" className="block text-sm font-medium text-gray-700">
            保管温度の上限（℃）
          </label>
          <input
            id="maxCelsius"
            type="number"
            step="any"
            value={value.maxCelsius}
            onChange={(event) => onChange({ ...value, maxCelsius: event.target.value })}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>
      </div>

      <p className="text-sm text-gray-600">
        下限は上限以下にします。下限が上限を超えた条件は、荷役で必ず破られます。
      </p>
    </fieldset>
  )
}
