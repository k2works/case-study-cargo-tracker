import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  fetchRouteDesignRequest,
  calculateRoutes,
  selectRoute,
  confirmRoute,
  type RouteDesignRequest,
  type RouteCandidate,
} from '../api/routingApi';

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

function formatCost(candidate: RouteCandidate): string {
  return `${candidate.currency} ${candidate.estimatedCost.toLocaleString()}`;
}

/**
 * 経路設計ワークベンチ（S14、US08/US09/US11）。
 *
 * <p>経路設計待ちリストの予約について、経路候補を算出・選択・確定し、確定経路を予約に紐付ける。
 * 期限内に到達可能な候補がない場合は条件緩和を促す警告を表示する。</p>
 */
export default function RouteDesignWorkbenchPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [request, setRequest] = useState<RouteDesignRequest | null>(null);
  const [candidates, setCandidates] = useState<RouteCandidate[] | null>(null);
  const [selectedSeq, setSelectedSeq] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [warning, setWarning] = useState<string | null>(null);

  useEffect(() => {
    if (!bookingId) return;
    fetchRouteDesignRequest(bookingId)
      .then(setRequest)
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [bookingId]);

  const resetFeedback = useCallback(() => {
    setError(null);
    setMessage(null);
    setWarning(null);
  }, []);

  async function handleCalculate() {
    if (!bookingId) return;
    resetFeedback();
    setSelectedSeq(null);
    try {
      const result = await calculateRoutes(bookingId);
      setCandidates(result);
      if (result.length === 0) {
        setWarning('期限内に到達可能な経路がありません。条件を緩和して再算出してください');
      } else {
        setMessage(`経路候補を ${result.length} 件算出しました`);
        setSelectedSeq(result[0].sequence);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '経路候補の算出に失敗しました');
    }
  }

  async function handleSelect() {
    if (!bookingId || selectedSeq == null) return;
    resetFeedback();
    try {
      await selectRoute(bookingId, selectedSeq);
      setMessage('経路を確定しました');
    } catch (e) {
      setError(e instanceof Error ? e.message : '経路の確定に失敗しました');
    }
  }

  async function handleConfirm() {
    if (!bookingId || selectedSeq == null) return;
    resetFeedback();
    try {
      await confirmRoute(bookingId, selectedSeq);
      navigate(`/bookings/${bookingId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '経路の予約紐付けに失敗しました');
    }
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">経路設計ワークベンチ</h1>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}
      {warning && (
        <div className="mb-4 rounded-md bg-yellow-50 p-3">
          <p role="alert" className="text-sm text-yellow-700">{warning}</p>
        </div>
      )}
      {message && (
        <div className="mb-4 rounded-md bg-green-50 p-3">
          <p className="text-sm text-green-700">{message}</p>
        </div>
      )}

      {request && (
        <dl className="mb-4 grid grid-cols-2 gap-3 rounded-lg border bg-white p-4">
          <div><dt className="text-xs text-gray-500">予約番号</dt><dd className="text-sm text-gray-900">{request.bookingId}</dd></div>
          <div><dt className="text-xs text-gray-500">出発地</dt><dd className="text-sm text-gray-900">{request.originUnlocode}</dd></div>
          <div><dt className="text-xs text-gray-500">目的地</dt><dd className="text-sm text-gray-900">{request.destinationUnlocode}</dd></div>
          <div><dt className="text-xs text-gray-500">到着期限</dt><dd className="text-sm text-gray-900">{request.arrivalDeadline}</dd></div>
          <div><dt className="text-xs text-gray-500">貨物種別</dt><dd className="text-sm text-gray-900">{cargoTypeLabel(request.cargoType)}</dd></div>
        </dl>
      )}

      <button
        type="button"
        onClick={handleCalculate}
        className="mb-4 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
      >
        経路候補を算出
      </button>

      {candidates && candidates.length > 0 && (
        <>
          <table className="w-full overflow-hidden rounded-lg border bg-white text-sm">
            <thead className="bg-gray-50 text-left text-xs text-gray-500">
              <tr>
                <th className="px-3 py-2">選択</th>
                <th className="px-3 py-2">経由港</th>
                <th className="px-3 py-2">所要日数</th>
                <th className="px-3 py-2">概算費用</th>
                <th className="px-3 py-2">航海番号</th>
                <th className="px-3 py-2">推奨</th>
              </tr>
            </thead>
            <tbody>
              {candidates.map((c) => (
                <tr key={c.sequence} className="border-t">
                  <td className="px-3 py-2">
                    <input
                      type="radio"
                      name="candidate"
                      aria-label={`候補${c.sequence}を選択`}
                      checked={selectedSeq === c.sequence}
                      onChange={() => setSelectedSeq(c.sequence)}
                    />
                  </td>
                  <td className="px-3 py-2">{c.ports.join(' → ')}</td>
                  <td className="px-3 py-2">{c.estimatedDays} 日</td>
                  <td className="px-3 py-2">{formatCost(c)}</td>
                  <td className="px-3 py-2">{c.voyageNumbers.join(', ')}</td>
                  <td className="px-3 py-2">{c.recommended ? '★' : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <p className="mt-4 text-xs text-gray-500">
            候補を 1 件選び、「① 経路を確定」→「② 予約に紐付け」の順に実行してください。
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <button
              type="button"
              disabled={selectedSeq == null}
              onClick={handleSelect}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-300"
            >
              ① 経路を確定
            </button>
            <span className="text-gray-400">→</span>
            <button
              type="button"
              disabled={selectedSeq == null}
              onClick={handleConfirm}
              className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:bg-gray-300"
            >
              ② 予約に紐付け
            </button>
          </div>
        </>
      )}

      <button
        type="button"
        onClick={() => navigate('/routing/design')}
        className="mt-4 block rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
      >
        待ちリストに戻る
      </button>
    </div>
  );
}
