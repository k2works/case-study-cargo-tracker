import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import { formatPortDateTime } from '@/shared/api/portTimeZone';
import { useAuthStore } from '@/shared/auth/authStore';
import { fetchHandlingHistory, voidHandling } from './api';

/** 履歴は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * 荷役履歴（S51 / UC13）。**荷役と追跡の両方が使う**（ui_design.md:236）。
 *
 * <p><b>取り消した記録も出す。</b> 消えていると、現場で何が起きたのかを
 * 後から突き合わせられない。取り消した印と理由を添える。</p>
 */
/**
 * 1 件の記録の状態（S51 の「状態」欄）。
 *
 * <p>取消が最優先——取り消した記録の「予定外」は、もう起きていないことの印。</p>
 */
function stateLabel(item: { voided: boolean; voidReason: string | null; offRoute: boolean }) {
  if (item.voided) {
    return '取消';
  }
  return item.offRoute ? '予定外' : '記録済';
}

/**
 * 誰がいつ取り消したか（M13）。
 *
 * <p><b>取り消しは現場の記録を後から変える操作</b>なので、誰がやったかが
 * 読めないと、荷主から問われたときに突き合わせられない。</p>
 *
 * <p><b>分からない行は「—」。</b> 列が無かったころの記録を 500 で落とすのは
 * 違う（`cargo_revision.updated_by` と同じ扱い）。</p>
 */
function voidedByLabel(item: { voided: boolean; voidedBy: string | null;
  voidedAt: string | null }) {
  if (!item.voided) {
    return '—';
  }
  const who = item.voidedBy ?? '—';
  return item.voidedAt === null ? who : `${who}（${formatBusinessDateTime(item.voidedAt)}）`;
}

export function HandlingHistoryPage() {
  const { trackingNumber = '' } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const isTracker = useAuthStore((state) => state.user?.roles.includes('ROLE_TRACKER') ?? false);
  // **記録は現場が取り消す。** 追跡管理者は履歴を読むだけ（記録した本人でないと
  // 「何を取り違えたか」が分からない）。
  const isHandler = useAuthStore((state) => state.user?.roles.includes('ROLE_HANDLER') ?? false);
  const queries = useQueryClient();
  const [voidingId, setVoidingId] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  const history = useQuery({
    queryKey: ['handling-history', trackingNumber],
    queryFn: () => fetchHandlingHistory(trackingNumber),
    enabled: trackingNumber !== '',
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = history.data?.state === 'ready' ? history.data.value.items : [];

  const cancel = useMutation({
    mutationFn: (activityId: string) => voidHandling(activityId, reason.trim()),
    onSuccess: async () => {
      setVoidingId(null);
      setReason('');
      await queries.invalidateQueries({ queryKey: ['handling-history', trackingNumber] });
    },
  });

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
                <th className={TH}>取り消した人</th>
                {/* **監査で最初に問われるのは理由。** 状態欄に括弧で混ぜると、
                    絞り込みも並べ替えもできず、長い理由で表が崩れる
                    （IT10 レビュー N17）。 */}
                <th className={TH}>理由</th>
                {isHandler && <th className={TH}>操作</th>}
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.activityId}>
                  {/* **港のローカル時刻で出し、JST を併記する**（non_functional.md:212 /
                      H.7）。片方だけにすると、どちらかの側が必ず換算を強いられる。 */}
                  <td className={TD}>
                    {formatPortDateTime(item.completedAt, item.unLocode)}
                  </td>
                  <td className={TD}>{item.handlingTypeLabel}</td>
                  <td className={TD}>{item.unLocode}</td>
                  <td className={TD}>{item.voyageNumber ?? '—'}</td>
                  <td className={TD}>{item.operator}</td>
                  <td className={TD}>
                    {stateLabel(item)}
                  </td>
                  <td className={TD}>{voidedByLabel(item)}</td>
                  <td className={TD}>
                    {item.voided ? (item.voidReason ?? '理由なし') : '—'}
                  </td>
                  {isHandler && (
                    <td className={TD}>
                      {/* **取り消せるのは取り消していない記録だけ。**
                          二度目の取り消しは集約が断るので、押せるボタンを並べない。 */}
                      {!item.voided && (
                        <button
                          type="button"
                          className={LINK}
                          onClick={() => {
                            setVoidingId(item.activityId);
                            setReason('');
                          }}
                        >
                          取り消す
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* **理由を書かせてから取り消す。** 取消の理由は履歴に残り、あとで
          「何を取り違えたのか」を突き合わせる唯一の手がかりになる。 */}
      {voidingId !== null && (
        <section className={`${CARD} mt-4`}>
          <h2 className="font-semibold">記録を取り消す</h2>
          <label htmlFor="voidReason" className={`${LABEL} mt-2`}>
            取り消す理由
          </label>
          <input
            id="voidReason"
            className={FIELD}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="別の貨物と取り違えました"
          />
          {cancel.isError && (
            <p role="alert" className={`${ALERT} mt-2`}>取り消せませんでした。</p>
          )}
          <div className="mt-4 flex gap-2">
            <button
              type="button"
              className={BUTTON_PRIMARY}
              disabled={reason.trim() === '' || cancel.isPending}
              onClick={() => cancel.mutate(voidingId)}
            >
              取り消しを確定する
            </button>
            <button type="button" className={LINK} onClick={() => setVoidingId(null)}>
              やめる
            </button>
          </div>
        </section>
      )}

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
