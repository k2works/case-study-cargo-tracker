import { type SubmitEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchVoyage, registerVoyage, updateVoyage } from '../api/voyageApi';

interface FormValues {
  voyageNumber: string;
  carrierCode: string;
  carrierName: string;
  shipName: string;
  originUnlocode: string;
  destUnlocode: string;
  departureDate: string;
  arrivalDate: string;
}

const empty: FormValues = {
  voyageNumber: '',
  carrierCode: '',
  carrierName: '',
  shipName: '',
  originUnlocode: '',
  destUnlocode: '',
  departureDate: '',
  arrivalDate: '',
};

function toDatetimeLocal(iso: string) {
  return iso ? iso.slice(0, 16) : '';
}

export default function VoyageFormPage() {
  const { voyageNumber } = useParams<{ voyageNumber: string }>();
  const isEdit = !!voyageNumber;
  const navigate = useNavigate();

  const [values, setValues] = useState<FormValues>(empty);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isEdit) return;
    fetchVoyage(voyageNumber)
      .then((v) => {
        setValues({
          voyageNumber: v.voyageNumber,
          carrierCode: v.carrierCode,
          carrierName: v.carrierName,
          shipName: v.shipName,
          originUnlocode: v.originUnlocode,
          destUnlocode: v.destUnlocode,
          departureDate: toDatetimeLocal(v.departureDate),
          arrivalDate: toDatetimeLocal(v.arrivalDate),
        });
      })
      .catch((e) => setError(e.message));
  }, [isEdit, voyageNumber]);

  function set(field: keyof FormValues) {
    return (e: React.ChangeEvent<HTMLInputElement>) =>
      setValues((prev) => ({ ...prev, [field]: e.target.value }));
  }

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (!values.voyageNumber || !values.carrierCode || !values.carrierName ||
        !values.shipName || !values.originUnlocode || !values.destUnlocode ||
        !values.departureDate || !values.arrivalDate) {
      setError('必須項目をすべて入力してください');
      return;
    }

    setLoading(true);
    try {
      if (isEdit) {
        await updateVoyage(voyageNumber, {
          departureDate: values.departureDate,
          arrivalDate: values.arrivalDate,
          movements: [],
          acceptedCargoTypes: [],
        });
      } else {
        await registerVoyage({
          voyageNumber: values.voyageNumber,
          carrierCode: values.carrierCode,
          carrierName: values.carrierName,
          shipName: values.shipName,
          originUnlocode: values.originUnlocode,
          destUnlocode: values.destUnlocode,
          departureDate: values.departureDate,
          arrivalDate: values.arrivalDate,
          movements: [],
          acceptedCargoTypes: [],
        });
      }
      navigate('/voyages');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  const submitLabel = isEdit ? '更新' : '登録';

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <h1 className="mb-6 text-xl font-bold text-gray-900">
        {isEdit ? '航海スケジュール更新' : '航海スケジュール新規登録'}
      </h1>

      <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border bg-white p-6 shadow-sm">
        {!isEdit && (
          <div>
            <label htmlFor="voyageNumber" className="block text-sm font-medium text-gray-700">
              航海番号
            </label>
            <input
              id="voyageNumber"
              type="text"
              value={values.voyageNumber}
              onChange={set('voyageNumber')}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        )}

        {!isEdit && (
          <>
            <div>
              <label htmlFor="carrierCode" className="block text-sm font-medium text-gray-700">
                運送会社コード
              </label>
              <input
                id="carrierCode"
                type="text"
                value={values.carrierCode}
                onChange={set('carrierCode')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="carrierName" className="block text-sm font-medium text-gray-700">
                運送会社名
              </label>
              <input
                id="carrierName"
                type="text"
                value={values.carrierName}
                onChange={set('carrierName')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="shipName" className="block text-sm font-medium text-gray-700">
                船名
              </label>
              <input
                id="shipName"
                type="text"
                value={values.shipName}
                onChange={set('shipName')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="originUnlocode" className="block text-sm font-medium text-gray-700">
                出発港 (UN/LOCODE)
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
              <label htmlFor="destUnlocode" className="block text-sm font-medium text-gray-700">
                到着港 (UN/LOCODE)
              </label>
              <input
                id="destUnlocode"
                type="text"
                value={values.destUnlocode}
                onChange={set('destUnlocode')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </>
        )}

        <div>
          <label htmlFor="departureDate" className="block text-sm font-medium text-gray-700">
            出発日
          </label>
          <input
            id="departureDate"
            type="datetime-local"
            value={values.departureDate}
            onChange={set('departureDate')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="arrivalDate" className="block text-sm font-medium text-gray-700">
            到着日
          </label>
          <input
            id="arrivalDate"
            type="datetime-local"
            value={values.arrivalDate}
            onChange={set('arrivalDate')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        {error && (
          <div className="rounded-md bg-red-50 p-3">
            <p role="alert" className="text-sm text-red-600">{error}</p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={loading}
            className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? '保存中...' : submitLabel}
          </button>
          <button
            type="button"
            onClick={() => navigate('/voyages')}
            className="rounded-md bg-gray-100 px-6 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
          >
            キャンセル
          </button>
        </div>
      </form>
    </div>
  );
}
