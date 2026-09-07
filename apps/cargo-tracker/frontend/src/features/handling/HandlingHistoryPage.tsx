import { useQuery } from '@tanstack/react-query';
import { useState, type SubmitEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { useAuthStore } from '@/shared/auth/authStore';
import { fetchHandlingHistory } from './api';

/** 履歴は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * 荷役履歴（S51 / UC13）。**荷役と追跡の両方が使う**（ui_design.md:236）。
 *
 * <p><b>取り消した記録も出す。</b> 消えていると、現場で何が起きたのかを
 * 後から突き合わせられない。取り消した印と理由を添える。</p>
 */
export function HandlingHistoryPage() {
  const { trackingNumber = '' } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const isTracker = useAuthStore((state) => state.user?.roles.includes('ROLE_TRACKER') ?? false);

  const history = useQuery({
    queryKey: ['handling-history', trackingNumber],
    queryFn: () => fetchHandlingHistory(trackingNumber),
    enabled: trackingNumber !== '',
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = history.data?.state === 'ready' ? history.data.value.items : [];

  if (trackingNumber === '') {
    // **番号なしでも開ける。** ナビからはここに来る（追跡管理者は問い合わせを
    // 受けて番号で引き、荷役作業員は自分が記録した貨物を確かめる）。
    return (
      <div>
        <h1 className={PAGE_TITLE}>荷役履歴</h1>
        <form
          className={`${CARD} mt-4`}
          onSubmit={(event: SubmitEvent<HTMLFormElement>) => {
            event.preventDefault();
            if (input.trim() !== '') {
              navigate(`/handling/${input.trim().toUpperCase()}`);
            }
          }}
        >
          <label htmlFor="trackingNumberInput" className={LABEL}>
            追跡番号
          </label>
          <input
            id="trackingNumberInput"
            className={FIELD}
            value={input}
            onChange={(event) => setInput(event.target.value.toUpperCase())}
            placeholder="TRK-AB12CD3456"
          />
          <button type="submit" className={`${BUTTON_PRIMARY} mt-4`}>
            履歴を見る
          </button>
        </form>
      </div>
    );
  }

  return (
    <div>
      <h1 className={PAGE_TITLE}>
        荷役履歴{'\u3000'}<span className="font-mono">{trackingNumber}</span>
      </h1>

      {history.isError && (
        <output className={`${ALERT} mt-4`}>荷役履歴を取得できませんでした。</output>
      )}

      <section className={`${CARD} mt-4 overflow-x-auto`}>
        {items.length === 0 ? (
          <p className="text-sm text-gray-600">
            この貨物の荷役はまだ記録されていません。港で作業が記録されると、ここに並びます。
          </p>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>荷役履歴</caption>
            <thead>
              <tr>
                <th className={TH}>日時</th>
                <th className={TH}>作業</th>
                <th className={TH}>場所</th>
                <th className={TH}>航海</th>
                <th className={TH}>記録者</th>
                <th className={TH}>状態</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.activityId}>
                  <td className={TD}>{formatBusinessDateTime(item.completedAt)}</td>
                  <td className={TD}>{item.handlingTypeLabel}</td>
                  <td className={TD}>{item.unLocode}</td>
                  <td className={TD}>{item.voyageNumber ?? '—'}</td>
                  <td className={TD}>{item.operator}</td>
                  <td className={TD}>
                    {item.voided
                      ? `取消（${item.voidReason ?? '理由なし'}）`
                      : item.offRoute ? '予定外' : '記録済'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* **共有画面のリンクもロールで出し分ける。** 荷役ロールに追跡詳細を
          出すと 403 になる（S41 は追跡と荷主）。 */}
      {isTracker && (
        <p className="mt-6 text-sm">
          <Link to={`/tracking/${trackingNumber}`} className={LINK}>
            追跡を見る
          </Link>
        </p>
      )}
    </div>
  );
}
