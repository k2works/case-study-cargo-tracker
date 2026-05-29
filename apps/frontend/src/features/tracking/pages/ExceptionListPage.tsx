import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  exceptionTypeLabel,
  fetchAllExceptions,
  responseStatusLabel,
  type ResponseStatus,
  type TrackingExceptionView,
} from '../api/trackingApi';
import type { PageResponse } from '../../../shared/api/types';

type FilterValue = ResponseStatus | 'ALL';

/**
 * S19 例外対応一覧（US19 / US20 / IT6 タスク 2.4 + 3.3）。
 *
 * <p>追跡管理者が全例外を横断して確認するダッシュボード。escalation 中（紛失）の例外を
 * 視覚的に強調表示し、フィルタで対応状況を絞り込めるようにする。</p>
 */
export default function ExceptionListPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<FilterValue>('ALL');
  const [data, setData] = useState<PageResponse<TrackingExceptionView> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const size = 20;

  useEffect(() => {
    let cancelled = false;
    fetchAllExceptions(filter, page, size)
      .then((d) => {
        if (!cancelled) {
          setData(d);
          setError(null);
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '取得に失敗しました');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [filter, page]);

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">例外対応一覧</h1>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <div className="mb-4 flex items-center gap-3">
        <label className="text-sm font-medium text-gray-700">
          <span>対応状態</span>
          <select
            value={filter}
            onChange={(e) => {
              setFilter(e.target.value as FilterValue);
              setPage(0);
            }}
            className="ml-2 rounded-md border-gray-300 shadow-sm text-sm"
          >
            <option value="ALL">全て</option>
            <option value="REPORTED">未対応</option>
            <option value="RESPONDING">対応中</option>
            <option value="RESOLVED">解決済</option>
          </select>
        </label>
        {data && (
          <span className="text-sm text-gray-500">
            {data.totalCount} 件
          </span>
        )}
      </div>

      <section className="rounded-lg border bg-white p-4 shadow-sm">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase text-gray-500">
              <th className="py-2">!</th>
              <th className="py-2">追跡番号</th>
              <th className="py-2">種別</th>
              <th className="py-2">発生日時</th>
              <th className="py-2">発生場所</th>
              <th className="py-2">対応状態</th>
              <th className="py-2">escalation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {(data?.items ?? []).map((e) => (
              <tr
                key={e.exceptionId}
                onClick={() => navigate(`/tracking/${e.trackingNumber}/manage`)}
                className={`cursor-pointer hover:bg-gray-50 ${
                  e.escalated ? 'bg-red-50' : ''
                }`}
              >
                <td className="py-2 text-gray-600">{e.escalated ? '⚠' : ''}</td>
                <td className="py-2 font-medium text-gray-900">{e.trackingNumber}</td>
                <td className="py-2 text-gray-600">{exceptionTypeLabel(e.type)}</td>
                <td className="py-2 text-gray-600">{e.occurredAt}</td>
                <td className="py-2 text-gray-600">{e.occurredUnlocode ?? '-'}</td>
                <td className="py-2 text-gray-600">{responseStatusLabel(e.responseStatus)}</td>
                <td className="py-2 text-gray-600">
                  {e.escalated ? (
                    <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700">
                      管理職通知済
                    </span>
                  ) : (
                    '-'
                  )}
                </td>
              </tr>
            ))}
            {(data?.items ?? []).length === 0 && (
              <tr>
                <td colSpan={7} className="py-4 text-center text-gray-400">
                  例外がありません
                </td>
              </tr>
            )}
          </tbody>
        </table>

        {data && data.totalCount > size && (
          <div className="mt-3 flex justify-between text-sm text-gray-600">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(p - 1, 0))}
              className="rounded-md bg-gray-100 px-3 py-1 disabled:opacity-50"
            >
              前へ
            </button>
            <span>
              ページ {data.page + 1} / {Math.ceil(data.totalCount / size)}
            </span>
            <button
              type="button"
              disabled={(data.page + 1) * size >= data.totalCount}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md bg-gray-100 px-3 py-1 disabled:opacity-50"
            >
              次へ
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
