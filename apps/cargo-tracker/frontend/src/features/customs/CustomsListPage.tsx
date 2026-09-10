import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { useAuthStore } from '@/shared/auth/authStore';
import {
  fetchCustomsDeclarations,
  SEARCHABLE_STATUSES,
  type CustomsSearchCondition,
  type CustomsStatus,
} from './api';

/** 一覧は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

const INITIAL: CustomsSearchCondition = {
  includeCleared: false,
  trackingNumber: '',
  status: '',
  overdueOnly: false,
};

/**
 * S52 通関申告一覧（UC21 / US29 §受入基準 6・7）。
 *
 * <p><b>既定で通関済を外す。</b> 決着したものが混ざると、一覧全体が「まだ手を
 * 入れる場所」に見えなくなる。読み口が外して返す。</p>
 *
 * <p><b>並べ直さない。</b> 留置営業日数が多い順（督促の対象が先）をサーバが決める。
 * 画面で並べ直すと判定が 2 か所になり、片方だけが正しい形になる。留置中の日数は
 * 日が経つだけで変わるので、<b>数えるのもサーバである</b>。</p>
 */
export function CustomsListPage() {
  // **登録は荷役ロール**（ui_design.md の画面一覧）。追跡管理者は状態を更新する側で、
  // 登録画面を開けない。リンクを出すと 403 に当たる——「開けない場所へ誘う」ことになる。
  const isHandler = useAuthStore(
    (state) => state.user?.roles.includes('ROLE_HANDLER') ?? false);
  const [condition, setCondition] = useState<CustomsSearchCondition>(INITIAL);
  const declarations = useQuery({
    queryKey: ['customs-declarations', condition],
    queryFn: () => fetchCustomsDeclarations(condition),
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = declarations.data?.state === 'ready' ? declarations.data.value.items : [];
  const overdue = items.filter((item) => item.overdue).length;

  return (
    <div>
      <h1 className={PAGE_TITLE}>通関申告一覧</h1>
      <p className="mt-1 text-sm text-gray-600">
        留置が長いものから並びます。
        {condition.includeCleared
          ? '通関済のものも出ています。'
          : '通関済のものは出ません。'}
      </p>

      <section className={`${CARD} mt-4`}>
        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label className={LABEL} htmlFor="customs-tracking-number">追跡番号</label>
            <input
              id="customs-tracking-number"
              className={FIELD}
              value={condition.trackingNumber}
              onChange={(event) =>
                setCondition({ ...condition, trackingNumber: event.target.value })}
            />
          </div>
          <div>
            <label className={LABEL} htmlFor="customs-status">通関状態</label>
            <select
              id="customs-status"
              className={FIELD}
              value={condition.status}
              onChange={(event) =>
                setCondition({
                  ...condition,
                  status: event.target.value as CustomsStatus | '',
                })}
            >
              {SEARCHABLE_STATUSES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={condition.overdueOnly}
              onChange={(event) =>
                setCondition({ ...condition, overdueOnly: event.target.checked })}
            />
            <span>督促の対象だけ表示</span>
          </label>
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={condition.includeCleared}
              onChange={(event) =>
                setCondition({ ...condition, includeCleared: event.target.checked })}
            />
            <span>通関済も表示</span>
          </label>
        </div>
      </section>

      {declarations.data?.state === 'pending' && (
        <output className={`${NOTICE} mt-4 block`}>{declarations.data.message}</output>
      )}
      {declarations.isError && (
        <p role="alert" className={`${ALERT} mt-4`}>一覧を取得できませんでした</p>
      )}

      {/* **気づく手段は次の行動へ繋ぐ。** 件数だけを出しても、どの申告に手を
          入れればよいか分からない。並びの先頭がその対象である。 */}
      {overdue > 0 && (
        <p role="alert" className={`${ALERT} mt-4`}>
          留置が 3 営業日を超えた申告が {overdue} 件あります。
        </p>
      )}

      <section className={`${CARD} mt-4 overflow-x-auto`}>
        {items.length === 0 ? (
          <output className={NOTICE}>
            条件に合う通関申告はありません。登録した申告はここに並びます。
          </output>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>通関申告</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>申告番号</th>
                <th scope="col" className={TH}>追跡番号</th>
                <th scope="col" className={TH}>状態</th>
                <th scope="col" className={TH}>申告日時</th>
                <th scope="col" className={TH}>留置営業日数</th>
                <th scope="col" className={TH}>最後の理由</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.declarationNumber}>
                  <td className={TD}>
                    <Link
                      to={`/customs/${encodeURIComponent(item.declarationNumber)}`}
                      className={LINK}
                    >
                      {item.declarationNumber}
                    </Link>
                  </td>
                  <td className={TD}>
                    {/* 追跡詳細へ辿れる。通関の話は「どの貨物の話か」から始まる。 */}
                    <Link to={`/tracking/${item.trackingNumber}`} className={LINK}>
                      {item.trackingNumber}
                    </Link>
                  </td>
                  <td className={TD}>
                    {/* 呼び名はサーバが返す。画面で対応表を持たない。 */}
                    {item.statusLabel}
                    {item.overdue && (
                      <span className="ml-2 rounded bg-red-100 px-2 py-0.5 text-xs
                        font-semibold text-red-800">
                        督促
                      </span>
                    )}
                  </td>
                  <td className={TD}>{formatBusinessDateTime(item.declaredAt)}</td>
                  <td className={TD}>
                    {/* **「営業日」と書く**（ui_design.md）。暦日と読まれると、
                        3 日超の意味が変わる。 */}
                    {item.status === 'HELD' ? `${item.heldBusinessDays} 営業日` : '—'}
                  </td>
                  <td className={TD}>{item.lastReason ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {isHandler && (
        <p className="mt-4">
          <Link to="/customs/new" className={BUTTON_PRIMARY}>通関申告を登録する</Link>
        </p>
      )}
    </div>
  );
}
