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
import {
  fetchTracking,
  notifyShipperOfException,
  resolveException,
  startExceptionResponse,
  updateTransportStatus,
  type TrackingExceptionView,
  type TrackingView,
} from './api';

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
  // **`[経路を再設計]` は経路設計者だけ**（US28 §受入基準 4）。他ロールには
  // 「依頼済み」と出す——押せない操作を並べると、できることが読めなくなる。
  const isRouting = useAuthStore(
    (state) => state.user?.roles.includes('ROLE_ROUTING') ?? false);

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
            ? '追跡番号が見つかりません。追跡番号をお確かめください。'
              + '追跡番号は予約が確定して発行されると使えます。'
              + '自社の貨物のはずが見つからないときは、営業担当者にお問い合わせください。'
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

      {view.misrouted && (
        <div role="alert" className={`${ALERT} mt-4`}>
          <p className="font-semibold">誤配を検知しました。</p>
          <p className="mt-1 text-sm">
            予定ルート外で荷役が記録されています。現在地:{' '}
            {view.currentUnLocode ?? '—'}。到着予定は誤配前の旅程によるもので、
            再設計するまで更新されません。
          </p>
          <p className="mt-2 text-sm">
            {isRouting ? (
              <Link to={`/routing/bookings/${view.bookingId}`} className={LINK}>
                経路を再設計
              </Link>
            ) : (
              '経路設計者に再設計を依頼済みです。'
            )}
          </p>
        </div>
      )}

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

      <History history={view.history} showRecordedBy={isTracker} />

      <ExceptionPanel
        trackingNumber={view.trackingNumber}
        exceptions={view.exceptions}
        canRespond={isTracker}
        onChanged={() => queries.invalidateQueries({ queryKey: ['tracking', trackingNumber] })}
      />

      {isTracker && <UpdateStatusPanel
        view={view}
        onUpdated={() => queries.invalidateQueries({ queryKey: ['tracking', trackingNumber] })}
      />}

      <p className="mt-6 text-sm">
        <Link to="/tracking" className={LINK}>
          追跡一覧に戻る
        </Link>
        {/* **起票の入口は詳細から。** 例外は「この貨物に起きたこと」なので、
            追跡番号を書き写させない（US19 §受入基準 1）。 */}
        {isTracker && (
          <>
            {' ／ '}
            <Link to={`/tracking/${view.trackingNumber}/exceptions/new`} className={LINK}>
              例外を起票する
            </Link>
          </>
        )}
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

/**
 * 状態の履歴（S41 / US17 §3）。
 *
 * <p><b>記録者は社内の利用者名なので荷主には出さない。</b> S41 は荷主も開く。
 * `ui_design.md` は荷主向け（S46）で「担当者名は出しません」と定めており、
 * 同じ判断をここにも通す。</p>
 */
function History({
  history,
  showRecordedBy,
}: {
  readonly history: TrackingView['history'];
  readonly showRecordedBy: boolean;
}) {
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
              {showRecordedBy && <th className={TH}>記録者</th>}
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
                {showRecordedBy && <td className={TD}>{event.recordedBy ?? '—'}</td>}
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
  // **後から入れる。** 出港は夜間で、記録は翌朝になる。空ならサーバの業務時計で「いま」。
  const [occurredAt, setOccurredAt] = useState('');

  const update = useMutation({
    mutationFn: () =>
      updateTransportStatus(view.trackingNumber, { newStatus, location, occurredAt }),
    onSuccess: () => {
      setNewStatus('');
      setLocation('');
      setOccurredAt('');
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
            onChange={(event) => setLocation(event.target.value.toUpperCase())}
            placeholder="JPTYO"
            maxLength={5}
            pattern="[A-Z]{5}"
          />
        </div>
        <div>
          <label htmlFor="occurredAt" className={LABEL}>
            起きた日時
          </label>
          <input
            id="occurredAt"
            type="datetime-local"
            className={FIELD}
            value={occurredAt}
            onChange={(event) => setOccurredAt(event.target.value)}
          />
          {/* **後から入れる。** 出港は夜間で、記録は翌朝になる。入れないと履歴の
              日時が全部「入力した時刻」になり、遅延の判断に使えない。 */}
          <p className="mt-1 text-xs text-gray-600">空のままなら「いま」で記録します。</p>
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

/**
 * 断った理由。<b>業務の判断で断ったなら、その文言をそのまま出す。</b>
 *
 * <p>通信の失敗と業務の拒否を同じ文言にすると、利用者は「やり直せばよいのか」
 * 「入力を直すのか」を判断できない。</p>
 */
function failureMessage(error: unknown): string {
  return error instanceof ApiError
    ? error.body.message
    : '記録できませんでした。もう一度お試しください。';
}

/**
 * 例外の一覧と対応（S41 / US19 §受入基準 3・4・5）。
 *
 * <p><b>解決したものも出す</b>（不変条件 6）。事実は消えず、料金調整の根拠になる。
 * 消えるのは操作のほうで、決着した例外に押せるボタンを並べない。</p>
 *
 * <p><b>対応するのは追跡管理者だけ。</b> 荷主は起きていることを読めるが、
 * 手は入れられない（サーバも同じ宣言で断る）。</p>
 */
function ExceptionPanel({ trackingNumber, exceptions, canRespond, onChanged }: Readonly<{
  trackingNumber: string;
  exceptions: readonly TrackingExceptionView[];
  canRespond: boolean;
  onChanged: () => void;
}>) {
  const [respondingTo, setRespondingTo] = useState<string | null>(null);
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [notifyingId, setNotifyingId] = useState<string | null>(null);
  const [plan, setPlan] = useState('');
  const [newEstimatedArrival, setNewEstimatedArrival] = useState('');
  const [resolution, setResolution] = useState('');
  const [means, setMeans] = useState('');
  const [summary, setSummary] = useState('');

  function close() {
    setRespondingTo(null);
    setResolvingId(null);
    setNotifyingId(null);
    setPlan('');
    setNewEstimatedArrival('');
    setResolution('');
    setMeans('');
    setSummary('');
  }

  const respond = useMutation({
    mutationFn: (exceptionId: string) =>
      startExceptionResponse(trackingNumber, exceptionId, { newEstimatedArrival, plan }),
    onSuccess: () => { close(); onChanged(); },
  });
  const resolve = useMutation({
    mutationFn: (exceptionId: string) =>
      resolveException(trackingNumber, exceptionId, resolution.trim()),
    onSuccess: () => { close(); onChanged(); },
  });
  const notify = useMutation({
    mutationFn: (exceptionId: string) =>
      notifyShipperOfException(trackingNumber, exceptionId, { means, summary }),
    onSuccess: () => { close(); onChanged(); },
  });

  if (exceptions.length === 0) {
    return null;
  }

  return (
    <section className={`${CARD} mt-4`}>
      <h2 className={SECTION_TITLE}>例外</h2>
      <ul className="mt-2 divide-y divide-gray-100">
        {exceptions.map((item) => (
          <li key={item.exceptionId} className="py-3">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-semibold text-gray-900">{item.exceptionTypeLabel}</span>
              {/* **緊急は種別が決める**（不変条件 7）。画面で判定しない。 */}
              {item.urgent && (
                <span className="rounded bg-red-100 px-2 py-0.5 text-xs font-semibold
                  text-red-800">
                  緊急
                </span>
              )}
              <span className="text-gray-600">{item.responseStatusLabel}</span>
              <span className="text-gray-600">
                {formatBusinessDateTime(item.occurredAt)}
              </span>
              <span className="text-gray-600">{item.unLocode ?? '—'}</span>
            </div>
            <p className="mt-1 text-sm text-gray-900">{item.description}</p>
            {item.responsePlan !== null && (
              <p className="mt-1 text-sm text-gray-700">
                <span className="text-gray-600">対応方針</span>{' '}
                <span>{item.responsePlan}</span>
                {item.newEstimatedArrival !== null && (
                  <>
                    {'\u3000'}
                    <span className="text-gray-600">新しい到着予定日</span>{' '}
                    <span>{item.newEstimatedArrival}</span>
                  </>
                )}
              </p>
            )}
            {item.resolution !== null && (
              <p className="mt-1 text-sm text-gray-700">
                <span className="text-gray-600">対応</span>{' '}
                <span>{item.resolution}</span>
              </p>
            )}
            {/* **知らせた記録が読めることでしか US19 §3 は満たせない。**
                送信基盤はスコープ外で、通知そのものは電話・メールで行う。 */}
            {item.notifications.length > 0 && (
              <ul className="mt-1 space-y-0.5 text-sm text-gray-700">
                {item.notifications.map((notice) => (
                  <li key={`${notice.notifiedAt}-${notice.means}`}>
                    <span className="text-gray-600">荷主へ連絡</span>{' '}
                    <span>{formatBusinessDateTime(notice.notifiedAt)}</span>{' '}
                    <span>{notice.means}</span>{' '}
                    <span>{notice.summary}</span>{' '}
                    <span className="text-gray-600">{notice.notifiedBy ?? '—'}</span>
                  </li>
                ))}
              </ul>
            )}

            {/* **決着した例外に押せるボタンを並べない。** 押しても集約が断る。 */}
            {canRespond && item.responseStatus !== 'RESOLVED' && (
              <div className="mt-2 flex flex-wrap gap-3 text-sm">
                {item.responseStatus === 'REPORTED' && (
                  <button
                    type="button"
                    className={LINK}
                    onClick={() => { close(); setRespondingTo(item.exceptionId); }}
                  >
                    対応を始める
                  </button>
                )}
                <button
                  type="button"
                  className={LINK}
                  onClick={() => { close(); setNotifyingId(item.exceptionId); }}
                >
                  荷主へ知らせた
                </button>
                <button
                  type="button"
                  className={LINK}
                  onClick={() => { close(); setResolvingId(item.exceptionId); }}
                >
                  解決にする
                </button>
              </div>
            )}

            {respondingTo === item.exceptionId && (
              <div className="mt-3 rounded border border-gray-200 p-3">
                <label htmlFor="newEstimatedArrival" className={LABEL}>
                  新しい到着予定日
                </label>
                <input
                  id="newEstimatedArrival"
                  type="date"
                  className={FIELD}
                  value={newEstimatedArrival}
                  onChange={(event) => setNewEstimatedArrival(event.target.value)}
                />
                <label htmlFor="plan" className={`${LABEL} mt-2`}>
                  対応方針
                </label>
                <input
                  id="plan"
                  className={FIELD}
                  value={plan}
                  onChange={(event) => setPlan(event.target.value)}
                  placeholder="代替便を手配中"
                />
                <div className="mt-3 flex gap-3">
                  <button
                    type="button"
                    className={BUTTON_PRIMARY}
                    disabled={plan.trim() === '' || respond.isPending}
                    onClick={() => respond.mutate(item.exceptionId)}
                  >
                    対応の開始を記録する
                  </button>
                  <button type="button" className={LINK} onClick={close}>やめる</button>
                </div>
              </div>
            )}

            {notifyingId === item.exceptionId && (
              <div className="mt-3 rounded border border-gray-200 p-3">
                {/* **送信基盤はスコープ外。** 通知は電話・メールで行い、
                    ここに残すのは「いつ・どうやって・何を伝えたか」だけ。 */}
                <p className="text-xs text-gray-600">
                  通知そのものは電話・メールで行います。ここには伝えた記録を残します。
                </p>
                <label htmlFor="means" className={`${LABEL} mt-2`}>伝えた手段</label>
                <input
                  id="means"
                  className={FIELD}
                  value={means}
                  onChange={(event) => setMeans(event.target.value)}
                  placeholder="電話"
                />
                <label htmlFor="summary" className={`${LABEL} mt-2`}>伝えた内容</label>
                <input
                  id="summary"
                  className={FIELD}
                  value={summary}
                  onChange={(event) => setSummary(event.target.value)}
                  placeholder="3 日遅れる見込みと伝えました"
                />
                <div className="mt-3 flex gap-3">
                  <button
                    type="button"
                    className={BUTTON_PRIMARY}
                    disabled={means.trim() === '' || summary.trim() === '' || notify.isPending}
                    onClick={() => notify.mutate(item.exceptionId)}
                  >
                    記録を残す
                  </button>
                  <button type="button" className={LINK} onClick={close}>やめる</button>
                </div>
              </div>
            )}

            {resolvingId === item.exceptionId && (
              <div className="mt-3 rounded border border-gray-200 p-3">
                <label htmlFor="resolution" className={LABEL}>対応内容</label>
                <input
                  id="resolution"
                  className={FIELD}
                  value={resolution}
                  onChange={(event) => setResolution(event.target.value)}
                  placeholder="代替便に振り替えました"
                />
                {/* **何をしたか読めない記録を残さない。** 集約も空を断る。 */}
                <p className="mt-1 text-xs text-gray-600">
                  解決しても例外は消えません。何をしたかが残ります。
                </p>
                <div className="mt-3 flex gap-3">
                  <button
                    type="button"
                    className={BUTTON_PRIMARY}
                    disabled={resolution.trim() === '' || resolve.isPending}
                    onClick={() => resolve.mutate(item.exceptionId)}
                  >
                    解決を確定する
                  </button>
                  <button type="button" className={LINK} onClick={close}>やめる</button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>

      {/* **断った理由をそのまま出す。** 固定文言にすると、集約が返した
          「解決した例外は変更できません」「対応内容は必須です」が誰にも届かず、
          利用者は次に何をすればよいか分からない（マニュアルもその文言で索く）。 */}
      {(respond.isError || resolve.isError || notify.isError) && (
        <output className={`${ALERT} mt-3`}>
          {failureMessage(respond.error ?? resolve.error ?? notify.error)}
        </output>
      )}
    </section>
  );
}
