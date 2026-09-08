import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
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
  const exceptions = useQuery({
    queryKey: ['open-exceptions'],
    queryFn: fetchOpenExceptions,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = exceptions.data?.state === 'ready' ? exceptions.data.value.items : [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>未解決の例外</h1>
      <p className="mt-1 text-sm text-gray-600">
        緊急のものから、到着期限までの残りが少ない順に並びます。解決したものは出ません。
      </p>

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
                <th className={TH}>種別</th>
                <th className={TH}>対応</th>
                <th className={TH}>発生</th>
                <th className={TH}>場所</th>
                <th className={TH}>到着期限</th>
                <th className={TH}>発生状況</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.exceptionId}>
                  <td className={TD}>
                    {/* **気づく手段は次の行動へ繋ぐ。** 一覧で終わると、
                        追跡管理者は追跡番号を書き写して S41 を探し直す。 */}
                    <Link to={`/tracking/${item.trackingNumber}`} className={LINK}>
                      {item.trackingNumber}
                    </Link>
                  </td>
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
                  <td className={TD}>{item.responseStatusLabel}</td>
                  <td className={TD}>{formatBusinessDateTime(item.occurredAt)}</td>
                  <td className={TD}>{item.unLocode ?? '—'}</td>
                  <td className={TD}>
                    {item.estimatedArrival === null
                      ? '—' : formatBusinessDateTime(item.estimatedArrival)}
                  </td>
                  <td className={TD}>{item.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
