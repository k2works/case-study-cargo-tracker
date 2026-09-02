import { SHIPPER_TYPE_LABELS, type ShipperType } from '../types'
import type { ShipperFormValue } from './shipper-form-types'

/**
 * 荷主の入力項目（登録・編集で共通）。
 *
 * 同じ項目を 2 つの画面に書き写すと、片方だけ直したときに「登録では入るのに
 * 編集では消える」種類のずれが生まれる。検証の文言も同じ理由で共有する。
 */
export function ShipperFormFields({
  value,
  onChange,
  typeLocked = false,
}: Readonly<{
  value: ShipperFormValue
  onChange: (next: ShipperFormValue) => void
  /** 編集では種別を変えられない。個人と法人ではその後に成り立つ規則が違う。 */
  typeLocked?: boolean
}>) {
  return (
    <>
      <div>
        <label htmlFor="type" className="block text-sm font-medium text-gray-700">
          荷主種別
        </label>
        <select
          id="type"
          value={value.type}
          disabled={typeLocked}
          onChange={(event) =>
            // 個人に戻したら契約情報は捨てる。残すと、画面に出ていない値が
            // 次に法人へ切り替えたときに黙って復活する
            onChange({
              ...value,
              type: event.target.value as ShipperType,
              contractNumber: '',
              discountRatePercent: '',
            })
          }
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2 disabled:bg-gray-100 disabled:text-gray-600"
        >
          {Object.entries(SHIPPER_TYPE_LABELS).map(([option, label]) => (
            <option key={option} value={option}>
              {label}
            </option>
          ))}
        </select>
        {typeLocked && (
          <p className="mt-1 text-sm text-gray-500">
            種別は変更できません。種別が違うなら、それは別の荷主です。
          </p>
        )}
      </div>

      {value.type === 'CORPORATE' && (
        <fieldset className="space-y-4 rounded border border-gray-200 bg-gray-50 p-4">
          <legend className="px-1 text-sm font-medium text-gray-700">法人契約情報</legend>

          <div>
            <label htmlFor="contractNumber" className="block text-sm font-medium text-gray-700">
              契約番号
            </label>
            <input
              id="contractNumber"
              type="text"
              value={value.contractNumber}
              onChange={(event) => onChange({ ...value, contractNumber: event.target.value })}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
            <p className="mt-1 text-sm text-gray-500">
              法人には契約番号が必要です。空のまま登録すると、精算時に契約が特定できません。
            </p>
          </div>

          <div>
            <label htmlFor="discountRatePercent" className="block text-sm font-medium text-gray-700">
              割引率（%）
            </label>
            <input
              id="discountRatePercent"
              type="number"
              step="any"
              value={value.discountRatePercent}
              onChange={(event) => onChange({ ...value, discountRatePercent: event.target.value })}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
            <p className="mt-1 text-sm text-gray-500">
              0〜30% の範囲。交渉中なら空のままにします（空欄は 0% ではなく「未設定」です）。
            </p>
          </div>
        </fieldset>
      )}

      <div>
        <label htmlFor="name" className="block text-sm font-medium text-gray-700">
          氏名/社名
        </label>
        <input
          id="name"
          type="text"
          value={value.name}
          onChange={(event) => onChange({ ...value, name: event.target.value })}
          required
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>

      <div>
        <label htmlFor="email" className="block text-sm font-medium text-gray-700">
          メールアドレス
        </label>
        <input
          id="email"
          type="email"
          value={value.email}
          onChange={(event) => onChange({ ...value, email: event.target.value })}
          required
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>

      <div>
        <label htmlFor="address" className="block text-sm font-medium text-gray-700">
          住所
        </label>
        <input
          id="address"
          type="text"
          value={value.address}
          onChange={(event) => onChange({ ...value, address: event.target.value })}
          required
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>

      <div>
        <label htmlFor="phone" className="block text-sm font-medium text-gray-700">
          連絡先（任意）
        </label>
        <input
          id="phone"
          type="tel"
          value={value.phone}
          onChange={(event) => onChange({ ...value, phone: event.target.value })}
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
        />
      </div>
    </>
  )
}
