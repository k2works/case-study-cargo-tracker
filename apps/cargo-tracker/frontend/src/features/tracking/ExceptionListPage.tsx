import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { useAuthStore } from '@/shared/auth/authStore';
import {
  CARD,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { fetchOpenExceptions } from './api';

/** 一覧は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * 例外一覧（S42 / UC16・US19 §受入基準 5）。**追跡管理者の作業一覧**。
 *
 * <p><b>並べ直さない。</b> 緊急が先、以降は到着期限までの残日数が少ない順
 * （不変条件 7）をサーバが決める。画面で並べ直すと判定が 2 か所になり、
 * 片方だけが正しい形になる。</p>
 *
 * <p><b>既定で解決済を外す。</b> 決着したものが混ざると、一覧全体が「まだ手を
 * 入れる場所」に見えなくなる。読み口が外して返す。</p>
 */
export function ExceptionListPage() {
  // **共有画面のリンクもロールで出し分ける。** 管理者はこの一覧を読む側で、
  // 追跡詳細（S41）と起票（S43）は開けない。リンクを出すと 403 に当たる
  // ——「開けない場所へ誘う」ことになり、緊急に気づいた人の足が止まる。
  const isTracker = useAuthStore(
    (state) => state.user?.roles.includes('ROLE_TRACKER') ?? false);
  const [includeResolved, setIncludeResolved] = useState(false);
  const exceptions = useQuery({
    queryKey: ['open-exceptions', includeResolved],
    queryFn: () => fetchOpenExceptions({ includeResolved }),
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = exceptions.data?.state === 'ready' ? exceptions.data.value.items : [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>例外一覧</h1>
      <p className="mt-1 text-sm text-gray-600">
        緊急のものから、到着期限までの残りが少ない順に並びます。
        {includeResolved
          ? '解決したものは未解決のあとに続きます。'
          : '解決したものは出ません。'}
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={includeResolved}
            onChange={(event) => setIncludeResolved(event.target.checked)}
          />
          {/* **解決済も参照できる。** 誤配の事実は解決後も料金調整の根拠になる
              （US28 §受入基準 8）。IT10 では S41 まで辿らないと見られなかった。 */}
          解決済も表示する
        </label>
      </div>

      <section className={`${CARD} mt-4 overflow-x-auto`}>
        {items.length === 0 ? (
          <output className={NOTICE}>
            未解決の例外はありません。起票された例外はここに並びます。
          </output>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>未解決の例外</caption>
            <thead>
              <tr>
                <th className={TH}>追跡番号</th>
                <th className={TH}>予約番号</th>
                <th className={TH}>種別</th>
                <th className={TH}>対応</th>
                <th className={TH}>発生</th>
                <th className={TH}>場所</th>
                <th className={TH}>到着期限</th>
                <th className={TH}>発生状況</th>
                <th className={TH}>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.exceptionId}>
                  <td className={TD}>
                    {/* **気づく手段は次の行動へ繋ぐ。** 一覧で終わると、
                        追跡管理者は追跡番号を書き写して S41 を探し直す。 */}
                    {isTracker ? (
                      <Link to={`/tracking/${item.trackingNumber}`} className={LINK}>
                        {item.trackingNumber}
                      </Link>
                    ) : (
                      item.trackingNumber
                    )}
                  </td>
                  {/* **電話は「A 社の予約の件で」から始まる**（IT10 レビュー N9）。
                      追跡番号だけでは、どの予約の話か照合できない。 */}
                  <td className={TD}>{item.bookingId ?? '—'}</td>
                  <td className={TD}>
                    {item.exceptionTypeLabel}
                    {/* **緊急は種別が決める**（不変条件 7）。画面で判定しない。 */}
                    {item.urgent && (
                      <span className="ml-2 rounded bg-red-100 px-2 py-0.5 text-xs
                        font-semibold text-red-800">
                        緊急
                      </span>
                    )}
                  </td>
                  <td className={TD}>
                    {item.responseStatusLabel}
                    {/* **緊急なのに誰にも伝わっていない**を一覧で見分ける
                        （US20 §受入基準 3）。知らせた時刻があれば済んでいる。 */}
                    {item.urgent && item.escalatedAt === null && (
                      <span className="ml-2 rounded bg-amber-100 px-2 py-0.5 text-xs
                        font-semibold text-amber-900">
                        未連絡
                      </span>
                    )}
                  </td>
                  <td className={TD}>{formatBusinessDateTime(item.occurredAt)}</td>
                  <td className={TD}>{item.unLocode ?? '—'}</td>
                  <td className={TD}>
                    {/* **日付そのものを出す。** 対応で動いた期限を優先して
                        サーバが返しており（並びの根拠）、時刻は持たない。 */}
                    {item.estimatedArrival ?? '—'}
                  </td>
                  <td className={TD}>{item.description}</td>
                  <td className={TD}>
                    {/* **起票への導線を一覧に置く**（IT10 レビュー N10）。遅延の
                        対応中に破損が見つかることはあり、そのとき追跡管理者は
                        いま見ている行から起票したい。S41 を経由させると、
                        追跡番号を書き写す手間が挟まる。 */}
                    {isTracker ? (
                      <Link
                        to={`/tracking/${item.trackingNumber}/exceptions/new`}
                        className={LINK}
                      >
                        例外を起票
                      </Link>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
