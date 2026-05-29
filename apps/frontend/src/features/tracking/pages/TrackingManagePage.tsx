import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  allowedNextStatuses,
  fetchTracking,
  fetchTrackingEvents,
  transportStatusLabel,
  updateTrackingStatus,
  type TrackingEvent,
  type TrackingSummary,
  type TransportStatus,
} from '../api/trackingApi';

export default function TrackingManagePage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>();
  const navigate = useNavigate();
  const [summary, setSummary] = useState<TrackingSummary | null>(null);
  const [events, setEvents] = useState<TrackingEvent[]>([]);
  const [toStatus, setToStatus] = useState<TransportStatus | ''>('');
  const [unlocode, setUnlocode] = useState('');
  const [voyageNumber, setVoyageNumber] = useState('');
  const [occurredAt, setOccurredAt] = useState(() => defaultOccurredAt());
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trackingNumber]);

  async function load() {
    try {
      const s = await fetchTracking(trackingNumber);
      setSummary(s);
      const e = await fetchTrackingEvents(trackingNumber);
      setEvents(e);
    } catch (e) {
      setError(e instanceof Error ? e.message : '取得に失敗しました');
    }
  }

  const next = summary ? allowedNextStatuses(summary.currentStatus) : [];

  async function handleSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    if (!toStatus) {
      setError('遷移先状態を選択してください');
      return;
    }
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      await updateTrackingStatus(trackingNumber, {
        toStatus,
        unlocode: unlocode || null,
        voyageNumber: voyageNumber || null,
        occurredAt,
        description: description || null,
      });
      setNotice(`状態を ${transportStatusLabel(toStatus)} に更新しました`);
      setToStatus('');
      setDescription('');
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '状態更新に失敗しました');
    } finally {
      setSubmitting(false);
    }
  }

  if (!summary) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-6">
        {error ? <p role="alert" className="text-red-600">{error}</p> : <p>読み込み中…</p>}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">追跡詳細・状態管理</h1>
        <button
          type="button"
          onClick={() => navigate('/tracking')}
          className="text-sm text-blue-600 hover:underline"
        >
          一覧に戻る
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}
      {notice && (
        <div className="mb-4 rounded-md bg-green-50 p-3">
          <p className="text-sm text-green-700">{notice}</p>
        </div>
      )}

      <section className="mb-6 rounded-lg border bg-white p-4 shadow-sm">
        <h2 className="mb-3 text-lg font-semibold">現在の追跡情報</h2>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-gray-500">追跡番号</dt>
          <dd className="font-medium">{summary.trackingNumber}</dd>
          <dt className="text-gray-500">予約 ID</dt>
          <dd>{summary.bookingId}</dd>
          <dt className="text-gray-500">現在状態</dt>
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
          <dt className="text-gray-500">航海</dt>
          <dd>{summary.currentVoyageNumber ?? '-'}</dd>
          <dt className="text-gray-500">最終更新</dt>
          <dd>{summary.lastEventAt ?? '-'}</dd>
        </dl>
      </section>

      <section className="mb-6 rounded-lg border bg-white p-4 shadow-sm">
        <h2 className="mb-3 text-lg font-semibold">状態を手動更新</h2>
        {next.length === 0 ? (
          <p className="text-sm text-gray-500">
            現在の状態（{transportStatusLabel(summary.currentStatus)}）から更新可能な遷移はありません。
          </p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700">
                遷移先状態
                <select
                  value={toStatus}
                  onChange={(e) => setToStatus(e.target.value as TransportStatus | '')}
                  required
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                >
                  <option value="">選択してください</option>
                  {next.map((s) => (
                    <option key={s} value={s}>
                      {transportStatusLabel(s)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <label className="block text-sm font-medium text-gray-700">
                現在地（UN/LOCODE）
                <input
                  type="text"
                  value={unlocode}
                  onChange={(e) => setUnlocode(e.target.value.toUpperCase())}
                  maxLength={5}
                  placeholder="JPTYO"
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
              <label className="block text-sm font-medium text-gray-700">
                航海番号
                <input
                  type="text"
                  value={voyageNumber}
                  onChange={(e) => setVoyageNumber(e.target.value)}
                  placeholder="V-MAERSK-220"
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
            </div>
            <label className="block text-sm font-medium text-gray-700">
              発生日時
              <input
                type="datetime-local"
                value={occurredAt}
                onChange={(e) => setOccurredAt(e.target.value)}
                required
                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
              />
            </label>
            <label className="block text-sm font-medium text-gray-700">
              説明
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={2}
                placeholder="状態変更の理由・メモ"
                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
              />
            </label>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting ? '送信中…' : '状態を更新'}
            </button>
          </form>
        )}
      </section>

      <section className="rounded-lg border bg-white p-4 shadow-sm">
        <h2 className="mb-3 text-lg font-semibold">追跡履歴</h2>
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase text-gray-500">
              <th className="py-2">発生日時</th>
              <th className="py-2">種別</th>
              <th className="py-2">状態</th>
              <th className="py-2">場所</th>
              <th className="py-2">航海</th>
              <th className="py-2">記録元</th>
              <th className="py-2">説明</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {events.map((e) => (
              <tr key={e.eventId}>
                <td className="py-2 text-gray-600">{e.occurredAt}</td>
                <td className="py-2 text-gray-600">{e.eventType}</td>
                <td className="py-2 text-gray-600">
                  {e.transportStatus ? transportStatusLabel(e.transportStatus) : '-'}
                </td>
                <td className="py-2 text-gray-600">{e.unlocode ?? '-'}</td>
                <td className="py-2 text-gray-600">{e.voyageNumber ?? '-'}</td>
                <td className="py-2 text-gray-600">{e.source ?? '-'}</td>
                <td className="py-2 text-gray-600">{e.description ?? '-'}</td>
              </tr>
            ))}
            {events.length === 0 && (
              <tr>
                <td colSpan={7} className="py-4 text-center text-gray-400">
                  履歴がありません
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function defaultOccurredAt(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}
