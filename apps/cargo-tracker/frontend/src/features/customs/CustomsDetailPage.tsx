import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import {
  fetchCustomsDeclaration,
  fetchCustomsHistory,
  updateCustomsStatus,
  UPDATABLE_STATUSES,
  type CustomsStatus,
} from './api';

/** 履歴の種別を業務の言葉にする。**知らない種別はそのまま出す**（欄が消えるより読める）。 */
const KIND_LABELS: Record<string, string> = {
  REGISTERED: '登録',
  STATUS_CHANGED: '状態の変更',
  CLEARANCE_NOTIFIED: '通関完了の連絡',
};

/**
 * S53 通関申告（UC21 / US29 §受入基準 2・4・8）。
 *
 * <p><b>状態の更新は送信中表示</b>（ui_design.md）。複数ロールが同じ申告を触るので、
 * 先行表示にすると「押せたのか分からない」を生む。</p>
 *
 * <p><b>理由は必須</b>（不変条件 2）。履歴はイベント列そのものなので、理由を落とすと
 * どこにも残らない。</p>
 */
export function CustomsDetailPage() {
  const { declarationNumber = '' } = useParams();
  const queries = useQueryClient();
  // **状態を更新するのは追跡管理者**（ui_design.md の画面一覧）。荷役作業員は
  // 申告を出す側で、更新のフォームを出しても 403 に当たる。
  const isTracker = useAuthStore(
    (state) => state.user?.roles.includes('ROLE_TRACKER') ?? false);
  const [status, setStatus] = useState<CustomsStatus>('CLEARED');
  const [reason, setReason] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const declaration = useQuery({
    queryKey: ['customs-declaration', declarationNumber],
    queryFn: () => fetchCustomsDeclaration(declarationNumber),
  });
  const history = useQuery({
    queryKey: ['customs-history', declarationNumber],
    queryFn: () => fetchCustomsHistory(declarationNumber),
  });

  const view = declaration.data?.state === 'ready' ? declaration.data.value : null;
  const entries = history.data?.state === 'ready' ? history.data.value.items : [];

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      await updateCustomsStatus(declarationNumber, { status, reason: reason.trim() });
      setReason('');
      // 反映は非同期。**先行表示はしない**ので、読み直して確定表示にする。
      await queries.invalidateQueries({ queryKey: ['customs-declaration'] });
      await queries.invalidateQueries({ queryKey: ['customs-history'] });
    } catch (failure) {
      setError(failure instanceof ApiError
        ? failure.message : '通関状態を更新できませんでした');
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <h1 className={PAGE_TITLE}>通関申告 {declarationNumber}</h1>

      {declaration.data?.state === 'pending' && (
        <output className={`${NOTICE} mt-4 block`}>{declaration.data.message}</output>
      )}
      {declaration.isError && (
        <p role="alert" className={`${ALERT} mt-4`}>通関申告を取得できませんでした</p>
      )}

      {view && (
        <>
          <section className={`${CARD} mt-4`}>
            <dl className="grid gap-2 sm:grid-cols-2">
              <div>
                <dt className="text-sm text-gray-600">追跡番号</dt>
                <dd>
                  <Link to={`/tracking/${view.trackingNumber}`} className={LINK}>
                    {view.trackingNumber}
                  </Link>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">通関状態</dt>
                <dd className="font-semibold">{view.statusLabel}</dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">申告日時</dt>
                <dd>{formatBusinessDateTime(view.declaredAt)}</dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">留置営業日数</dt>
                {/* **「営業日」と書く**（ui_design.md）。暦日と読まれると
                    3 日超の意味が変わる。 */}
                <dd>
                  {view.status === 'HELD' ? `${view.heldBusinessDays} 営業日` : '—'}
                </dd>
              </div>
            </dl>
          </section>

          {view.overdue && (
            <p role="alert" className={`${ALERT} mt-4`}>
              留置が 3 営業日を超えています。督促の対象です。
            </p>
          )}

          {isTracker && (
            <section className={`${CARD} mt-4`}>
              <h2 className={SECTION_TITLE}>状態を更新する</h2>
              {error && <p role="alert" className={`${ALERT} mt-3`}>{error}</p>}
              <form className="mt-3 grid gap-4" onSubmit={submit}>
                <div>
                  <label className={LABEL} htmlFor="customs-new-status">状態の変更</label>
                  <select
                    id="customs-new-status"
                    className={FIELD}
                    value={status}
                    onChange={(event) => setStatus(event.target.value as CustomsStatus)}
                  >
                    {UPDATABLE_STATUSES.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className={LABEL} htmlFor="customs-reason">理由</label>
                  <textarea
                    id="customs-reason"
                    className={FIELD}
                    required
                    rows={2}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                  />
                  <p className="mt-1 text-xs text-gray-600">
                    あとから「なぜ変わったのか」を読む人のために書きます。必須です。
                  </p>
                </div>
                <button type="submit" className={BUTTON_PRIMARY} disabled={sending}>
                  {sending ? '送信中…' : '状態を更新する'}
                </button>
              </form>
            </section>
          )}
        </>
      )}

      <section className={`${CARD} mt-4 overflow-x-auto`}>
        <h2 className={SECTION_TITLE}>状態の履歴</h2>
        {entries.length === 0 ? (
          <output className={`${NOTICE} mt-3 block`}>履歴はまだありません。</output>
        ) : (
          <table className={`${TABLE} mt-3`}>
            <caption className={TABLE_CAPTION}>状態の履歴</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>日時</th>
                <th scope="col" className={TH}>変更</th>
                <th scope="col" className={TH}>変更者</th>
                <th scope="col" className={TH}>理由</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={`${entry.kind}-${entry.changedAt}-${entry.reason ?? ''}`}>
                  <td className={TD}>{formatBusinessDateTime(entry.changedAt)}</td>
                  <td className={TD}>
                    {KIND_LABELS[entry.kind] ?? entry.kind}
                    {entry.statusLabel !== null && ` → ${entry.statusLabel}`}
                  </td>
                  {/* 通知の行は変更者を持たない（手作業で伝えた記録なので、
                      システムが送ったように読ませない）。 */}
                  <td className={TD}>{entry.changedBy ?? '—'}</td>
                  <td className={TD}>{entry.reason ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <p className="mt-4">
        <Link to="/customs" className={LINK}>通関申告一覧へ戻る</Link>
      </p>
    </div>
  );
}
