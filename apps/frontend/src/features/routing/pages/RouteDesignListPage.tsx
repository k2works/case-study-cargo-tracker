import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchRouteDesignRequests, type RouteDesignRequest } from '../api/routingApi';

function cargoTypeLabel(type: string): string {
  switch (type) {
    case 'GENERAL':
      return '一般';
    case 'HAZARDOUS':
      return '危険物';
    case 'REFRIGERATED':
      return '冷凍';
    default:
      return type;
  }
}

/**
 * 経路設計待ちリスト（US08 / S14 入口）。
 *
 * <p>cross-service で routingms に届いた経路設計依頼（route_design_request）を到着期限の昇順で表示し、
 * 行クリックで経路設計ワークベンチへ遷移する（レビュー H3 / L4）。</p>
 */
export default function RouteDesignListPage() {
  const navigate = useNavigate();
  const [requests, setRequests] = useState<RouteDesignRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchRouteDesignRequests()
      .then(setRequests)
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">経路設計待ちリスト</h1>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      {loading && <p className="text-sm text-gray-400">読み込み中...</p>}

      {!loading && requests.length === 0 && (
        <p className="text-sm text-gray-500">経路設計待ちの予約はありません。</p>
      )}

      {requests.length > 0 && (
        <table className="w-full overflow-hidden rounded-lg border bg-white text-sm">
          <thead className="bg-gray-50 text-left text-xs text-gray-500">
            <tr>
              <th className="px-4 py-2">予約番号</th>
              <th className="px-4 py-2">出発地</th>
              <th className="px-4 py-2">目的地</th>
              <th className="px-4 py-2">到着期限</th>
              <th className="px-4 py-2">貨物種別</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {requests.map((r) => (
              <tr key={r.bookingId} className="border-t">
                <td className="px-4 py-2 font-medium text-gray-900">{r.bookingId}</td>
                <td className="px-4 py-2">{r.originUnlocode}</td>
                <td className="px-4 py-2">{r.destinationUnlocode}</td>
                <td className="px-4 py-2">{r.arrivalDeadline}</td>
                <td className="px-4 py-2">{cargoTypeLabel(r.cargoType)}</td>
                <td className="px-4 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => navigate(`/routing/design/${r.bookingId}`)}
                    className="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700"
                  >
                    経路を設計
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
