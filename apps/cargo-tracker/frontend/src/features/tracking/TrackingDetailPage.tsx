import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  NOTICE,
  PAGE_TITLE,
  SECTION_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { ApiError } from '@/shared/api/client';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { useAuthStore } from '@/shared/auth/authStore';
import { fetchTracking, updateTransportStatus, type TrackingView } from './api';

/** 詳細も 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * 状態の呼び名。<b>正典は要素表（domain-model.md）で、サーバが呼び名で返す。</b>
 *
 * <p>ここに要るのは「動かせる先」の選択肢だけで、サーバは名前（{@code RECEIVED}）を
 * 返す。<b>呼び名を画面が持つのは重複だが、選択肢のためだけに 1 往復増やさない</b>
 * ——ずれると検査（{@code transportStatusLabels.test.ts}）が赤になる。</p>
 */
export const STATUS_LABELS: Record<string, string> = {
  NOT_RECEIVED: '未受領',
  RECEIVED: '受領済',
  LOADED: '積込済',
  IN_TRANSIT: '輸送中',
  UNLOADED: '荷降し済',
  AWAITING_CLAIM: '引取待ち',
  DELIVERED: '引取済',
  MISROUTED: '誤配',
  EXCEPTION: '例外発生',
};

/**
 * 追跡詳細・管理（S41 / UC14・UC15）。**追跡管理者と荷主の両方が使う**。
 *
 * <p><b>本 IT で出すのは「状態の履歴」と「状態を手動更新」だけ。</b> 例外・誤配・
 * 陸揚げ待ちは後続の IT で足す（中身の無い枠を先に置くと、動いていると誤解される）。</p>
 *
 * <p><b>荷主には更新の操作を出さない。</b> 自分の貨物の状態を書き換えられてしまう。
 * Gateway も {@code POST /status} を追跡管理者だけに絞っているので、画面の出し分けは
 * 二重の守りではなく<b>押してから断られないため</b>である。</p>
 */
export function TrackingDetailPage() {
  const { trackingNumber = '' } = useParams();
  const queries = useQueryClient();
  const isTracker = useAuthStore((state) => state.user?.roles.includes('ROLE_TRACKER') ?? false);

  const tracking = useQuery({
    queryKey: ['tracking', trackingNumber],
    queryFn: () => fetchTracking(trackingNumber),
    retry: false,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  if (tracking.isError) {
    return (
      <div>
        <h1 className={PAGE_TITLE}>追跡</h1>
        <output className={`${ALERT} mt-4`}>
          {tracking.error instanceof ApiError && tracking.error.status === 404
            ? '追跡が見つかりません。追跡番号をお確かめください。'
            : '追跡を取得できませんでした。'}
        </output>
        <p className="mt-4 text-sm">
          <Link to="/tracking" className={LINK}>
            追跡一覧に戻る
          </Link>
        </p>
      </div>
    );
  }

  if (tracking.data?.state !== 'ready') {
    return <p className="text-sm text-gray-600">読み込んでいます…</p>;
  }

  const view = tracking.data.value;

  return (
    <div>
      <h1 className={PAGE_TITLE}>
        <span className="font-mono">{view.trackingNumber}</span>{' '}
        <span className="rounded bg-blue-50 px-2 py-1 text-base text-blue-800">
          {view.statusLabel}
        </span>
      </h1>

      <section className={`${CARD} mt-4`}>
        <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
          <dt className="text-gray-600">区間</dt>
          <dd className="text-gray-900">
            {view.originUnLocode} → {view.destinationUnLocode}
          </dd>
          <dt className="text-gray-600">現在</dt>
          <dd className="text-gray-900">{view.currentUnLocode ?? '—'}</dd>
          <dt className="text-gray-600">到着予定</dt>
          <dd className="text-gray-900">
            {view.estimatedArrival === null
              ? '—'
              : formatBusinessDateTime(view.estimatedArrival)}
          </dd>
        </dl>
      </section>

      <History history={view.history} />

      {isTracker && <UpdateStatusPanel
        view={view}
        onUpdated={() => queries.invalidateQueries({ queryKey: ['tracking', trackingNumber] })}
      />}

      <p className="mt-6 text-sm">
        <Link to="/tracking" className={LINK}>
          追跡一覧に戻る
        </Link>
        {/* **共有画面のリンクもロールで出し分ける。** 荷主に出すと 403 になる。 */}
        {isTracker && (
          <>
            {' ／ '}
            <Link to={`/bookings/${view.bookingId}`} className={LINK}>
              予約を見る
            </Link>
          </>
        )}
      </p>
    </div>
  );
}

/** 状態の履歴（S41 / US17 §3）。 */
function History({ history }: { readonly history: TrackingView['history'] }) {
  return (
    <section className={`${CARD} mt-4 overflow-x-auto`}>
      <h2 className={SECTION_TITLE}>状態の履歴</h2>
      {history.length === 0 ? (
        <p className="mt-2 text-sm text-gray-600">
          まだ記録はありません。状態を更新すると、ここに残ります。
        </p>
      ) : (
        <table className={`${TABLE} mt-2`}>
          <caption className={TABLE_CAPTION}>状態の履歴</caption>
          <thead>
            <tr>
              <th className={TH}>日時</th>
              <th className={TH}>変更</th>
              <th className={TH}>場所</th>
              <th className={TH}>記録者</th>
            </tr>
          </thead>
          <tbody>
            {history.map((event) => (
              <tr key={`${event.occurredAt}-${event.statusLabel}`}>
                <td className={TD}>{formatBusinessDateTime(event.occurredAt)}</td>
                <td className={TD}>
                  {event.previousStatusLabel === null
                    ? event.statusLabel
                    : `${event.previousStatusLabel} → ${event.statusLabel}`}
                </td>
                <td className={TD}>{event.location ?? '—'}</td>
                <td className={TD}>{event.recordedBy ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

/**
 * 状態の手動更新（S41 / US17 §2）。<b>追跡管理者だけ</b>。
 *
 * <p><b>選べるのはサーバが返した「動かせる先」だけ。</b> 画面が遷移表を持つと
 * 判定が 2 つになり、集約が断る先を出してしまう。</p>
 */
function UpdateStatusPanel({
  view,
  onUpdated,
}: {
  readonly view: TrackingView;
  readonly onUpdated: () => void;
}) {
  const [newStatus, setNewStatus] = useState('');
  const [location, setLocation] = useState('');

  const update = useMutation({
    mutationFn: () => updateTransportStatus(view.trackingNumber, { newStatus, location }),
    onSuccess: () => {
      setNewStatus('');
      setLocation('');
      onUpdated();
    },
  });

  if (view.nextStatuses.length === 0) {
    return (
      <output className={`${NOTICE} mt-4`}>
        これ以上状態は動きません（{view.statusLabel}）。
      </output>
    );
  }

  return (
    <section className={`${CARD} mt-4`}>
      <h2 className={SECTION_TITLE}>状態を手動更新</h2>
      <p className="mt-1 text-sm text-gray-600">
        出港のように、荷役として記録されない動きを入れます。
      </p>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="newStatus" className={LABEL}>
            新しい状態
          </label>
          <select
            id="newStatus"
            className={FIELD}
            value={newStatus}
            onChange={(event) => setNewStatus(event.target.value)}
          >
            <option value="">選んでください</option>
            {view.nextStatuses.map((status) => (
              <option key={status} value={status}>
                {STATUS_LABELS[status] ?? status}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="location" className={LABEL}>
            場所（UN/LOCODE）
          </label>
          <input
            id="location"
            className={FIELD}
            value={location}
            onChange={(event) => setLocation(event.target.value)}
            placeholder="JPTYO"
          />
        </div>
      </div>

      <button
        type="button"
        className={`${BUTTON_PRIMARY} mt-4`}
        disabled={newStatus === '' || update.isPending}
        onClick={() => update.mutate()}
      >
        状態を更新する
      </button>

      {update.isError && (
        <output className={`${ALERT} mt-4`}>
          {update.error instanceof ApiError
            ? update.error.body.message
            : '状態を更新できませんでした。'}
        </output>
      )}
    </section>
  );
}
