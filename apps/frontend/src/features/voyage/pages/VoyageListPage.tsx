import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchVoyages, type Voyage } from '../api/voyageApi';

export default function VoyageListPage() {
  const navigate = useNavigate();
  const [voyages, setVoyages] = useState<Voyage[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchVoyages()
      .then(setVoyages)
      .catch((e) => setError(e.message));
  }, []);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">航海スケジュール一覧</h1>
        <button
          type="button"
          onClick={() => navigate('/voyages/new')}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          新規登録
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border bg-white shadow-sm">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">航海番号</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">船名</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">運送会社</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">出発港</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">到着港</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">出発日</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">到着日</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {voyages.map((v) => (
              <tr key={v.voyageNumber}>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{v.voyageNumber}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{v.shipName}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{v.carrierName}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{v.originUnlocode}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{v.destUnlocode}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{v.departureDate?.slice(0, 10)}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{v.arrivalDate?.slice(0, 10)}</td>
                <td className="px-4 py-3 text-right">
                  <button
                    type="button"
                    onClick={() => navigate(`/voyages/${v.voyageNumber}/edit`)}
                    className="rounded-md bg-gray-100 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-200"
                  >
                    編集
                  </button>
                </td>
              </tr>
            ))}
            {voyages.length === 0 && !error && (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-sm text-gray-400">
                  登録されている航海スケジュールはありません
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
