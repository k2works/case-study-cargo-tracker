import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import {
  fetchPublicTracking,
  transportStatusLabel,
  type PublicTrackingPayload,
  type PublicTrackingResult,
} from '../api/trackingApi';

/**
 * S15 追跡照会（公開、US18 / ADR-0013）。
 *
 * <p>荷主・荷受人が、メールで受け取った URL（{@code /tracking/{tn}?token=<JWT>}）から
 * ログイン不要でアクセスする公開ページ。PublicTrackingTokenFilter による JWT 検証
 * 失敗時は 403 を受信し、その場合はリンク再発行依頼メッセージを表示する。</p>
 */
export default function TrackingPublicPage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [result, setResult] = useState<PublicTrackingResult | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchPublicTracking(trackingNumber, token).then((r) => {
      if (!cancelled) setResult(r);
    });
    return () => {
      cancelled = true;
    };
  }, [trackingNumber, token]);

  if (!result) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6">
        <p className="text-gray-500">読み込み中…</p>
      </div>
    );
  }

  if (result.type === 'forbidden') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6">
        <h1 className="mb-4 text-xl font-bold text-gray-900">CargoTracker 追跡情報</h1>
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          <p className="font-semibold">アクセスできません</p>
          <p className="mt-1">{result.message}</p>
        </div>
      </div>
    );
  }

  if (result.type === 'not_found') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6">
        <h1 className="mb-4 text-xl font-bold text-gray-900">CargoTracker 追跡情報</h1>
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          追跡番号が見つかりません
        </div>
      </div>
    );
  }

  if (result.type === 'error') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6">
        <h1 className="mb-4 text-xl font-bold text-gray-900">CargoTracker 追跡情報</h1>
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {result.message}
        </div>
      </div>
    );
  }

  return <PublicTrackingView payload={result.payload} />;
}

function PublicTrackingView({ payload }: { payload: PublicTrackingPayload }) {
  const { summary, events } = payload;
  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">CargoTracker 追跡情報</h1>

      <section className="mb-6 rounded-lg border bg-white p-4 shadow-sm">
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-gray-500">追跡番号</dt>
          <dd className="font-medium">{summary.trackingNumber}</dd>
          <dt className="text-gray-500">現在の状態</dt>
          <dd className="font-medium">
            {transportStatusLabel(summary.currentStatus)}
            {summary.misrouted && (
              <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700">
                誤配送
              </span>
            )}
          </dd>
          <dt className="text-gray-500">現在地</dt>
          <dd>{summary.currentUnlocode ?? '-'}</dd>
          <dt className="text-gray-500">推定到着</dt>
          <dd>{summary.estimatedArrival ?? '-'}</dd>
          {summary.deliveredAt && (
            <>
              <dt className="text-gray-500">配送完了日時</dt>
              <dd>{summary.deliveredAt}</dd>
            </>
          )}
        </dl>
      </section>

      <section className="rounded-lg border bg-white p-4 shadow-sm">
        <h2 className="mb-3 text-lg font-semibold">追跡履歴</h2>
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase text-gray-500">
              <th className="py-2">日時</th>
              <th className="py-2">状態</th>
              <th className="py-2">場所</th>
              <th className="py-2">記録元</th>
              <th className="py-2">説明</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {events.map((e) => (
              <tr key={e.eventId}>
                <td className="py-2 text-gray-600">{e.occurredAt}</td>
                <td className="py-2 text-gray-600">
                  {e.transportStatus ? transportStatusLabel(e.transportStatus) : '-'}
                </td>
                <td className="py-2 text-gray-600">{e.unlocode ?? '-'}</td>
                <td className="py-2 text-gray-600">{e.source ?? '-'}</td>
                <td className="py-2 text-gray-600">{e.description ?? '-'}</td>
              </tr>
            ))}
            {events.length === 0 && (
              <tr>
                <td colSpan={5} className="py-4 text-center text-gray-400">
                  追跡履歴がありません
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <p className="mt-4 text-xs text-gray-500">
        このリンクは時限署名トークンで保護されています。第三者へのトークンの転送はお控えください。
      </p>
    </div>
  );
}
