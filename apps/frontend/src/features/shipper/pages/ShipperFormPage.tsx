import { type SubmitEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  fetchShippersByEmail,
  registerShipper,
  type Shipper,
  type ShipperType,
} from '../api/shipperApi';

interface FormValues {
  shipperType: ShipperType;
  name: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  countryCode: string;
  postalCode: string;
  email: string;
  phone: string;
  contractNumber: string;
  discountRatePercent: string; // UI では % 表現 (0-30)
}

const empty: FormValues = {
  shipperType: 'INDIVIDUAL',
  name: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  countryCode: '',
  postalCode: '',
  email: '',
  phone: '',
  contractNumber: '',
  discountRatePercent: '',
};

/** UI の割引率(%) 文字列を 0.000-0.300 の比率に変換する。空文字は null、不正値は NaN。 */
function parseDiscountRate(percent: string): number | null {
  if (percent === '') return null;
  const n = Number(percent);
  if (!Number.isFinite(n)) return Number.NaN;
  return Math.round(n * 1000) / 100000; // 0-30(%) → 0.000-0.300 (誤差対策で 3 桁丸め)
}

export default function ShipperFormPage() {
  const navigate = useNavigate();
  const [values, setValues] = useState<FormValues>(empty);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [duplicates, setDuplicates] = useState<Shipper[] | null>(null);

  function set<K extends keyof FormValues>(field: K) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setValues((prev) => ({ ...prev, [field]: e.target.value as FormValues[K] }));
  }

  async function submitRegistration() {
    setLoading(true);
    try {
      const isCorporate = values.shipperType === 'CORPORATE';
      const discountRateValue = isCorporate ? parseDiscountRate(values.discountRatePercent) : null;
      await registerShipper({
        shipperType: values.shipperType,
        name: values.name,
        addressLine1: values.addressLine1,
        addressLine2: values.addressLine2 || null,
        city: values.city,
        countryCode: values.countryCode,
        postalCode: values.postalCode || null,
        email: values.email,
        phone: values.phone,
        contractNumber: isCorporate ? values.contractNumber : null,
        discountRate: discountRateValue,
      });
      navigate('/shippers');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setDuplicates(null);

    if (!values.name || !values.addressLine1 || !values.city ||
        !values.countryCode || !values.email || !values.phone) {
      setError('必須項目をすべて入力してください');
      return;
    }

    if (values.shipperType === 'CORPORATE') {
      if (!values.contractNumber) {
        setError('法人荷主の契約番号は必須です');
        return;
      }
      const rate = parseDiscountRate(values.discountRatePercent);
      if (rate === null || Number.isNaN(rate) || rate < 0 || rate > 0.3) {
        setError('割引率は 0〜30(%) の範囲で入力してください');
        return;
      }
    }

    setLoading(true);
    try {
      const existing = await fetchShippersByEmail(values.email);
      if (existing.length > 0) {
        setDuplicates(existing);
        setLoading(false);
        return;
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '重複検出に失敗しました');
      setLoading(false);
      return;
    }

    await submitRegistration();
  }

  function handleProceedAsNew() {
    setDuplicates(null);
    void submitRegistration();
  }

  function handleUseExisting() {
    setDuplicates(null);
    navigate('/shippers');
  }

  const isCorporate = values.shipperType === 'CORPORATE';

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <h1 className="mb-6 text-xl font-bold text-gray-900">荷主新規登録</h1>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-4 rounded-lg border bg-white p-6 shadow-sm"
      >
        <div>
          <label htmlFor="shipperType" className="block text-sm font-medium text-gray-700">
            荷主種別
          </label>
          <select
            id="shipperType"
            value={values.shipperType}
            onChange={set('shipperType')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="INDIVIDUAL">個人</option>
            <option value="CORPORATE">法人</option>
          </select>
        </div>
        <div>
          <label htmlFor="name" className="block text-sm font-medium text-gray-700">
            氏名/社名
          </label>
          <input
            id="name"
            type="text"
            value={values.name}
            onChange={set('name')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="addressLine1" className="block text-sm font-medium text-gray-700">
            住所 1 行目
          </label>
          <input
            id="addressLine1"
            type="text"
            value={values.addressLine1}
            onChange={set('addressLine1')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="addressLine2" className="block text-sm font-medium text-gray-700">
            住所 2 行目 (任意)
          </label>
          <input
            id="addressLine2"
            type="text"
            value={values.addressLine2}
            onChange={set('addressLine2')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="city" className="block text-sm font-medium text-gray-700">
            市区町村
          </label>
          <input
            id="city"
            type="text"
            value={values.city}
            onChange={set('city')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="countryCode" className="block text-sm font-medium text-gray-700">
            国コード (ISO 3166-1)
          </label>
          <input
            id="countryCode"
            type="text"
            value={values.countryCode}
            onChange={set('countryCode')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="postalCode" className="block text-sm font-medium text-gray-700">
            郵便番号 (任意)
          </label>
          <input
            id="postalCode"
            type="text"
            value={values.postalCode}
            onChange={set('postalCode')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-gray-700">
            メールアドレス
          </label>
          <input
            id="email"
            type="email"
            value={values.email}
            onChange={set('email')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="phone" className="block text-sm font-medium text-gray-700">
            電話番号
          </label>
          <input
            id="phone"
            type="text"
            value={values.phone}
            onChange={set('phone')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        {isCorporate && (
          <div className="rounded-md border border-blue-100 bg-blue-50 p-4 space-y-4">
            <p className="text-sm font-medium text-blue-900">法人契約情報</p>
            <div>
              <label htmlFor="contractNumber" className="block text-sm font-medium text-gray-700">
                契約番号
              </label>
              <input
                id="contractNumber"
                type="text"
                value={values.contractNumber}
                onChange={set('contractNumber')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="discountRatePercent" className="block text-sm font-medium text-gray-700">
                割引率 (%) 0〜30
              </label>
              <input
                id="discountRatePercent"
                type="number"
                step="0.1"
                value={values.discountRatePercent}
                onChange={set('discountRatePercent')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </div>
        )}

        {error && (
          <div className="rounded-md bg-red-50 p-3">
            <p role="alert" className="text-sm text-red-600">{error}</p>
          </div>
        )}

        {duplicates && duplicates.length > 0 && (
          <div className="rounded-md border border-yellow-300 bg-yellow-50 p-4">
            <p className="text-sm font-medium text-yellow-800">
              既存荷主が見つかりました。どちらを使用しますか？
            </p>
            <ul className="mt-2 space-y-1 text-sm text-gray-700">
              {duplicates.map((d) => (
                <li key={d.shipperId}>
                  <span className="font-mono">{d.shipperId}</span> — {d.name} ({d.email})
                </li>
              ))}
            </ul>
            <div className="mt-3 flex gap-3">
              <button
                type="button"
                onClick={handleProceedAsNew}
                className="rounded-md bg-blue-600 px-4 py-2 text-xs font-medium text-white hover:bg-blue-700"
              >
                それでも新規登録する
              </button>
              <button
                type="button"
                onClick={handleUseExisting}
                className="rounded-md bg-gray-100 px-4 py-2 text-xs font-medium text-gray-700 hover:bg-gray-200"
              >
                既存荷主を使用する
              </button>
            </div>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={loading || duplicates !== null}
            className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? '保存中...' : '登録'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/shippers')}
            className="rounded-md bg-gray-100 px-6 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
          >
            キャンセル
          </button>
        </div>
      </form>
    </div>
  );
}
