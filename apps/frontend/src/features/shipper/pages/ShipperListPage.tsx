import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchShippers, type Shipper } from '../api/shipperApi';

export default function ShipperListPage() {
  const navigate = useNavigate();
  const [shippers, setShippers] = useState<Shipper[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchShippers()
      .then(setShippers)
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, []);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">荷主一覧</h1>
        <button
          type="button"
          onClick={() => navigate('/shippers/new')}
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
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">荷主 ID</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">種別</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">氏名/社名</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">メール</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">電話番号</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">市区町村</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {shippers.map((s) => (
              <tr key={s.shipperId}>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{s.shipperId}</td>
                <td className="px-4 py-3 text-sm text-gray-600">
                  {s.shipperType === 'CORPORATE' ? '法人' : '個人'}
                </td>
                <td className="px-4 py-3 text-sm text-gray-600">{s.name}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{s.email}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{s.phone}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{s.city}</td>
              </tr>
            ))}
            {shippers.length === 0 && !error && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  登録されている荷主はありません
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
