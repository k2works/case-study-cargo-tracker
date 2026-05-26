import { type SubmitEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { bookCargo, type CargoType } from '../api/bookingApi';
import { fetchShippersPage, type Shipper } from '../../shipper/api/shipperApi';

interface FormValues {
  shipperId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string;
  cargoType: CargoType;
  weightKg: string;
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  quantity: string;
  productName: string;
  hazardImoClass: string;
  hazardUnNumber: string;
  hazardDeclaration: string;
  temperatureMinC: string;
  temperatureMaxC: string;
}

const empty: FormValues = {
  shipperId: '',
  originUnlocode: '',
  destinationUnlocode: '',
  arrivalDeadline: '',
  cargoType: 'GENERAL',
  weightKg: '',
  lengthCm: '',
  widthCm: '',
  heightCm: '',
  quantity: '',
  productName: '',
  hazardImoClass: '',
  hazardUnNumber: '',
  hazardDeclaration: '',
  temperatureMinC: '',
  temperatureMaxC: '',
};

function parseOptionalInt(s: string): number | null {
  if (s === '') return null;
  const n = Number(s);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

function parseOptionalNumber(s: string): number | null {
  if (s === '') return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function validateHazardous(values: FormValues): string | null {
  if (!values.hazardImoClass || !values.hazardUnNumber || !values.hazardDeclaration) {
    return '危険物の場合は IMO 分類クラス・国連番号・申告文をすべて入力してください';
  }
  return null;
}

function validateRefrigerated(values: FormValues): string | null {
  if (!values.temperatureMinC || !values.temperatureMaxC) {
    return '冷凍貨物の場合は最低温度・最高温度をともに入力してください';
  }
  const min = Number(values.temperatureMinC);
  const max = Number(values.temperatureMaxC);
  if (!Number.isFinite(min) || !Number.isFinite(max)) {
    return '温度は数値で入力してください';
  }
  if (min > max) {
    return '最低温度は最高温度以下である必要があります';
  }
  return null;
}

/** 予約フォームのバリデーション。エラーがあれば最初のメッセージを、無ければ null を返す。 */
function validateBookingForm(values: FormValues): string | null {
  if (!values.shipperId || !values.originUnlocode || !values.destinationUnlocode ||
      !values.arrivalDeadline || !values.weightKg || !values.quantity || !values.productName) {
    return '必須項目をすべて入力してください';
  }
  if (values.originUnlocode === values.destinationUnlocode) {
    return '出発地と目的地は異なる必要があります';
  }
  const weight = Number(values.weightKg);
  if (!Number.isFinite(weight) || weight <= 0) {
    return '重量は 0 より大きい必要があります';
  }
  const quantity = Number(values.quantity);
  if (!Number.isFinite(quantity) || quantity <= 0) {
    return '数量は 0 より大きい必要があります';
  }
  if (values.cargoType === 'HAZARDOUS') return validateHazardous(values);
  if (values.cargoType === 'REFRIGERATED') return validateRefrigerated(values);
  return null;
}

export default function BookingFormPage() {
  const navigate = useNavigate();
  const [values, setValues] = useState<FormValues>(empty);
  const [shippers, setShippers] = useState<Shipper[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchShippersPage(0, 200)
      .then((p) => setShippers(p.items))
      .catch((e) => setError(e instanceof Error ? e.message : '荷主一覧の取得に失敗しました'));
  }, []);

  function set<K extends keyof FormValues>(field: K) {
    return (
      e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
    ) =>
      setValues((prev) => ({ ...prev, [field]: e.target.value as FormValues[K] }));
  }

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const validationError = validateBookingForm(values);
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    try {
      await bookCargo({
        shipperId: values.shipperId,
        originUnlocode: values.originUnlocode,
        destinationUnlocode: values.destinationUnlocode,
        arrivalDeadline: values.arrivalDeadline,
        cargoType: values.cargoType,
        weightKg: Number(values.weightKg),
        lengthCm: parseOptionalInt(values.lengthCm),
        widthCm: parseOptionalInt(values.widthCm),
        heightCm: parseOptionalInt(values.heightCm),
        quantity: Math.trunc(Number(values.quantity)),
        productName: values.productName,
        hazardImoClass: values.cargoType === 'HAZARDOUS' ? values.hazardImoClass : null,
        hazardUnNumber: values.cargoType === 'HAZARDOUS' ? values.hazardUnNumber : null,
        hazardDeclaration: values.cargoType === 'HAZARDOUS' ? values.hazardDeclaration : null,
        temperatureMinC: values.cargoType === 'REFRIGERATED' ? parseOptionalNumber(values.temperatureMinC) : null,
        temperatureMaxC: values.cargoType === 'REFRIGERATED' ? parseOptionalNumber(values.temperatureMaxC) : null,
      });
      navigate('/bookings');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <h1 className="mb-6 text-xl font-bold text-gray-900">貨物予約新規登録</h1>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-4 rounded-lg border bg-white p-6 shadow-sm"
      >
        <div>
          <label htmlFor="shipperId" className="block text-sm font-medium text-gray-700">
            荷主
          </label>
          <select
            id="shipperId"
            value={values.shipperId}
            onChange={set('shipperId')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="">-- 荷主を選択 --</option>
            {shippers.map((s) => (
              <option key={s.shipperId} value={s.shipperId}>
                {s.shipperId} - {s.name} ({s.shipperType === 'CORPORATE' ? '法人' : '個人'})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="cargoType" className="block text-sm font-medium text-gray-700">
            貨物種別
          </label>
          <select
            id="cargoType"
            value={values.cargoType}
            onChange={set('cargoType')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="GENERAL">一般貨物</option>
            <option value="HAZARDOUS">危険物</option>
            <option value="REFRIGERATED">冷凍・冷蔵貨物</option>
          </select>
        </div>

        <div>
          <label htmlFor="originUnlocode" className="block text-sm font-medium text-gray-700">
            出発地 (UN/LOCODE)
          </label>
          <input
            id="originUnlocode"
            type="text"
            value={values.originUnlocode}
            onChange={set('originUnlocode')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="destinationUnlocode" className="block text-sm font-medium text-gray-700">
            目的地 (UN/LOCODE)
          </label>
          <input
            id="destinationUnlocode"
            type="text"
            value={values.destinationUnlocode}
            onChange={set('destinationUnlocode')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="arrivalDeadline" className="block text-sm font-medium text-gray-700">
            到着期限
          </label>
          <input
            id="arrivalDeadline"
            type="date"
            value={values.arrivalDeadline}
            onChange={set('arrivalDeadline')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="weightKg" className="block text-sm font-medium text-gray-700">
            重量 (kg)
          </label>
          <input
            id="weightKg"
            type="number"
            step="0.01"
            value={values.weightKg}
            onChange={set('weightKg')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div className="grid grid-cols-3 gap-3">
          <div>
            <label htmlFor="lengthCm" className="block text-sm font-medium text-gray-700">
              長さ (cm)
            </label>
            <input
              id="lengthCm"
              type="number"
              value={values.lengthCm}
              onChange={set('lengthCm')}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label htmlFor="widthCm" className="block text-sm font-medium text-gray-700">
              幅 (cm)
            </label>
            <input
              id="widthCm"
              type="number"
              value={values.widthCm}
              onChange={set('widthCm')}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label htmlFor="heightCm" className="block text-sm font-medium text-gray-700">
              高さ (cm)
            </label>
            <input
              id="heightCm"
              type="number"
              value={values.heightCm}
              onChange={set('heightCm')}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>

        <div>
          <label htmlFor="quantity" className="block text-sm font-medium text-gray-700">
            数量
          </label>
          <input
            id="quantity"
            type="number"
            value={values.quantity}
            onChange={set('quantity')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="productName" className="block text-sm font-medium text-gray-700">
            品名
          </label>
          <input
            id="productName"
            type="text"
            value={values.productName}
            onChange={set('productName')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        {values.cargoType === 'HAZARDOUS' && (
          <fieldset className="rounded-md border border-amber-300 bg-amber-50 p-3 space-y-3">
            <legend className="px-2 text-sm font-medium text-amber-800">危険物申告情報</legend>
            <div>
              <label htmlFor="hazardImoClass" className="block text-sm font-medium text-gray-700">
                IMO 分類クラス
              </label>
              <input
                id="hazardImoClass"
                type="text"
                value={values.hazardImoClass}
                onChange={set('hazardImoClass')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="hazardUnNumber" className="block text-sm font-medium text-gray-700">
                国連番号
              </label>
              <input
                id="hazardUnNumber"
                type="text"
                value={values.hazardUnNumber}
                onChange={set('hazardUnNumber')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="hazardDeclaration" className="block text-sm font-medium text-gray-700">
                申告文
              </label>
              <textarea
                id="hazardDeclaration"
                rows={2}
                value={values.hazardDeclaration}
                onChange={set('hazardDeclaration')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </fieldset>
        )}

        {values.cargoType === 'REFRIGERATED' && (
          <fieldset className="rounded-md border border-sky-300 bg-sky-50 p-3 space-y-3">
            <legend className="px-2 text-sm font-medium text-sky-800">温度管理条件</legend>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="temperatureMinC" className="block text-sm font-medium text-gray-700">
                  最低温度 (℃)
                </label>
                <input
                  id="temperatureMinC"
                  type="number"
                  step="0.1"
                  value={values.temperatureMinC}
                  onChange={set('temperatureMinC')}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label htmlFor="temperatureMaxC" className="block text-sm font-medium text-gray-700">
                  最高温度 (℃)
                </label>
                <input
                  id="temperatureMaxC"
                  type="number"
                  step="0.1"
                  value={values.temperatureMaxC}
                  onChange={set('temperatureMaxC')}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
            </div>
          </fieldset>
        )}

        {error && (
          <div className="rounded-md bg-red-50 p-3">
            <p role="alert" className="text-sm text-red-600">{error}</p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={loading}
            className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? '保存中...' : '登録'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/bookings')}
            className="rounded-md bg-gray-100 px-6 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
          >
            キャンセル
          </button>
        </div>
      </form>
    </div>
  );
}
