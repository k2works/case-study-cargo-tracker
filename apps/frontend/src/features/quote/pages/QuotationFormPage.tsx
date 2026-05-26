import { useState, type SubmitEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { searchVoyages, type Voyage } from '../../voyage/api/voyageApi';
import { createQuotation, type RouteCandidateInput } from '../api/quoteApi';

const COST_PER_KG = 500;

interface FormState {
  shipperId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string;
  cargoType: string;
  weightKg: string;
  productName: string;
  validUntil: string;
}

function estimateDays(voyage: Voyage): number {
  const departure = new Date(voyage.departureDate).getTime();
  const arrival = new Date(voyage.arrivalDate).getTime();
  return Math.max(1, Math.round((arrival - departure) / 86_400_000));
}

const LABEL = 'block text-sm font-medium text-gray-700';
const INPUT = 'mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm';

export default function QuotationFormPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>({
    shipperId: '',
    originUnlocode: '',
    destinationUnlocode: '',
    arrivalDeadline: '',
    cargoType: 'GENERAL',
    weightKg: '',
    productName: '',
    validUntil: '',
  });
  const [voyages, setVoyages] = useState<Voyage[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function update(field: keyof FormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSearch() {
    setError(null);
    try {
      const result = await searchVoyages({
        origin: form.originUnlocode || undefined,
        destination: form.destinationUnlocode || undefined,
        cargoType: form.cargoType || undefined,
      });
      setVoyages(result);
      setSearched(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : '航海検索に失敗しました');
    }
  }

  function toggle(voyageNumber: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(voyageNumber)) {
        next.delete(voyageNumber);
      } else {
        next.add(voyageNumber);
      }
      return next;
    });
  }

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    const weight = Number(form.weightKg);
    const candidates: RouteCandidateInput[] = voyages
      .filter((v) => selected.has(v.voyageNumber))
      .map((v) => ({
        itinerarySummary: `${v.originUnlocode} → ${v.destUnlocode}（${v.voyageNumber}）`,
        estimatedDays: estimateDays(v),
        estimatedCost: Math.round(weight * COST_PER_KG),
        estimatedCurrency: 'JPY',
      }));
    try {
      await createQuotation({
        shipperId: form.shipperId,
        originUnlocode: form.originUnlocode,
        destinationUnlocode: form.destinationUnlocode,
        arrivalDeadline: form.arrivalDeadline,
        cargoType: form.cargoType,
        weightKg: weight,
        productName: form.productName || undefined,
        validUntil: form.validUntil,
        candidates,
      });
      navigate('/quotes');
    } catch (e) {
      setError(e instanceof Error ? e.message : '見積の作成に失敗しました');
    }
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">新規見積作成</h1>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="shipperId" className={LABEL}>荷主 ID</label>
            <input id="shipperId" className={INPUT}
              value={form.shipperId} onChange={(e) => update('shipperId', e.target.value)} />
          </div>
          <div>
            <label htmlFor="cargoType" className={LABEL}>貨物種別</label>
            <select id="cargoType" className={INPUT}
              value={form.cargoType} onChange={(e) => update('cargoType', e.target.value)}>
              <option value="GENERAL">一般</option>
              <option value="HAZARDOUS">危険物</option>
              <option value="REFRIGERATED">冷凍</option>
            </select>
          </div>
          <div>
            <label htmlFor="origin" className={LABEL}>出発地</label>
            <input id="origin" className={INPUT}
              value={form.originUnlocode} onChange={(e) => update('originUnlocode', e.target.value)} />
          </div>
          <div>
            <label htmlFor="destination" className={LABEL}>目的地</label>
            <input id="destination" className={INPUT}
              value={form.destinationUnlocode} onChange={(e) => update('destinationUnlocode', e.target.value)} />
          </div>
          <div>
            <label htmlFor="weightKg" className={LABEL}>重量(kg)</label>
            <input id="weightKg" type="number" className={INPUT}
              value={form.weightKg} onChange={(e) => update('weightKg', e.target.value)} />
          </div>
          <div>
            <label htmlFor="productName" className={LABEL}>品名</label>
            <input id="productName" className={INPUT}
              value={form.productName} onChange={(e) => update('productName', e.target.value)} />
          </div>
          <div>
            <label htmlFor="arrivalDeadline" className={LABEL}>希望期限</label>
            <input id="arrivalDeadline" type="date" className={INPUT}
              value={form.arrivalDeadline} onChange={(e) => update('arrivalDeadline', e.target.value)} />
          </div>
          <div>
            <label htmlFor="validUntil" className={LABEL}>見積有効期限</label>
            <input id="validUntil" type="date" className={INPUT}
              value={form.validUntil} onChange={(e) => update('validUntil', e.target.value)} />
          </div>
        </div>

        <button type="button" onClick={handleSearch}
          className="rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200">
          航海を検索
        </button>

        {searched && (
          <div className="rounded-lg border bg-white">
            <h2 className="border-b px-4 py-2 text-sm font-semibold text-gray-700">航海候補</h2>
            {voyages.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-gray-400">
                条件に合致する航海がありません。条件を緩和して再検索してください。
              </p>
            ) : (
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">選択</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">航海番号</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">運送会社</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">出発</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">到着</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {voyages.map((v) => (
                    <tr key={v.voyageNumber}>
                      <td className="px-3 py-2">
                        <input type="checkbox" aria-label={`候補 ${v.voyageNumber}`}
                          checked={selected.has(v.voyageNumber)} onChange={() => toggle(v.voyageNumber)} />
                      </td>
                      <td className="px-3 py-2 text-sm font-medium text-gray-900">{v.voyageNumber}</td>
                      <td className="px-3 py-2 text-sm text-gray-600">{v.carrierName}</td>
                      <td className="px-3 py-2 text-sm text-gray-600">{v.departureDate}</td>
                      <td className="px-3 py-2 text-sm text-gray-600">{v.arrivalDate}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        <div className="flex gap-2">
          <button type="submit"
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
            見積を作成
          </button>
          <button type="button" onClick={() => navigate('/quotes')}
            className="rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200">
            キャンセル
          </button>
        </div>
      </form>
    </div>
  );
}
