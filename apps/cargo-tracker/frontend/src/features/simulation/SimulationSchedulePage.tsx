import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
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
import {
  fetchSimulationSchedule,
  startSimulationSchedule,
  stopSimulationSchedule,
  type CountView,
} from './simulationApi';

/** 稼働中は 5 秒ごとに読み直す（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 5_000;

function CountTable({ caption, counts, header }: {
  readonly caption: string;
  readonly counts: readonly CountView[];
  readonly header: string;
}) {
  if (counts.length === 0) {
    return <p className="text-sm text-gray-600">{caption}はまだありません。</p>;
  }
  return (
    <table className={TABLE}>
      <caption className={TABLE_CAPTION}>{caption}</caption>
      <thead>
        <tr>
          <th scope="col" className={TH}>{header}</th>
          <th scope="col" className={TH}>件数</th>
        </tr>
      </thead>
      <tbody>
        {counts.map((count) => (
          <tr key={count.code}>
            <td className={TD}>{count.label}</td>
            <td className={TD}>{count.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * S94 継続実行と統計（管理者 / US36）。
 *
 * <p><b>S92（実行の一覧）とは別の画面にする。</b> 統計は「いま何が起きているか」で、
 * 一覧は「何を流したか」——混ぜると一覧の目的がぼやける（注 N6）。</p>
 *
 * <p><b>種を読めるようにする</b>（§3）。読めないと、同じ並びをもう一度流せない。</p>
 *
 * <p><b>止めてもすぐには止まらない。</b> 走っている実行は最後まで終える（§4）
 * ——「停止処理中」を状態として出さないと、画面が嘘をつく。</p>
 */
export function SimulationSchedulePage() {
  const client = useQueryClient();
  const [seed, setSeed] = useState('');
  const [error, setError] = useState<string | null>(null);

  const schedule = useQuery({
    queryKey: ['simulation-schedule'],
    queryFn: fetchSimulationSchedule,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const refresh = () => {
    setError(null);
    void client.invalidateQueries({ queryKey: ['simulation-schedule'] });
  };

  const start = useMutation({
    mutationFn: () => startSimulationSchedule(seed.trim() === '' ? null : Number(seed)),
    onSuccess: refresh,
    // **断りをそのまま出す。** 許可されていない環境・すでに動いている稼働、
    // どちらも読めなければ次の手が決まらない。
    onError: (failure: Error) => setError(failure.message),
  });
  const stop = useMutation({
    mutationFn: () => stopSimulationSchedule(),
    onSuccess: refresh,
    onError: (failure: Error) => setError(failure.message),
  });

  const latest = schedule.data?.state === 'ready' ? schedule.data.value : null;
  // **止めた稼働も返る**（US36 §3・§8）。止めた瞬間に件数も失敗工程の分布も
  // 乱数の種も読めなくなると、夜通し流して翌朝に結果を読む使い方が成り立たない。
  const running = latest !== null && latest.status !== 'STOPPED';
  // **止まっているときだけ開始の入口を出す。** 否定の分岐にすると読み違えやすい
  // ので、意味のある名前を与える。
  const stopped = !running;

  return (
    <section>
      <Link className={LINK} to="/admin/simulations">
        ← 業務シミュレーション
      </Link>
      <h1 className={`${PAGE_TITLE} mt-2`}>継続実行</h1>

      {error !== null && <p role="alert" className={`${ALERT} mt-4`}>{error}</p>}

      {stopped && (
        <div className={`${CARD} mt-4`}>
          <p className="text-sm text-gray-600">
            継続実行は動いていません。乱数の種を指定すると、同じ並びを再現できます
            （空欄なら自動で作って記録します）。
          </p>
          <label className="mt-3 flex items-center gap-2 text-sm">
            <span>乱数の種</span>
            <input
              className="rounded border border-gray-300 px-2 py-1"
              value={seed}
              inputMode="numeric"
              onChange={(event) => setSeed(event.target.value)}
            />
          </label>
          <button
            type="button"
            className={`${BUTTON_PRIMARY} mt-3`}
            disabled={start.isPending}
            onClick={() => start.mutate()}
          >
            継続実行を開始する
          </button>
        </div>
      )}

      {latest !== null && (
        <>
          <dl className={`${CARD} mt-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4`}>
            <div>
              <dt className="text-gray-600">状態</dt>
              <dd className="font-medium">{latest.statusLabel}</dd>
            </div>
            <div>
              {/* **種を読めるようにする**（§3）。読めないと再現できない。 */}
              <dt className="text-gray-600">乱数の種</dt>
              <dd className="font-medium">{latest.seed}</dd>
            </div>
            <div>
              <dt className="text-gray-600">実行間隔</dt>
              <dd className="font-medium">{latest.intervalSeconds} 秒</dd>
            </div>
            <div>
              <dt className="text-gray-600">同時実行</dt>
              <dd className="font-medium">
                {latest.runningNow} / {latest.maxConcurrent} 本
              </dd>
            </div>
            <div>
              <dt className="text-gray-600">開始</dt>
              <dd className="font-medium">{formatBusinessDateTime(latest.startedAt)}</dd>
            </div>
            <div>
              <dt className="text-gray-600">開始した人</dt>
              <dd className="font-medium">{latest.startedBy}</dd>
            </div>
          </dl>

          {latest.status === 'STOPPING' && (
            <output className={`${NOTICE} mt-4 block`}>
              停止処理中です。走っている実行が終わるまで待っています
              （{latest.runningNow} 本）。
            </output>
          )}

          {running && (
            <button
              type="button"
              className={`${BUTTON_PRIMARY} mt-4`}
              disabled={stop.isPending || latest.status === 'STOPPING'}
              onClick={() => stop.mutate()}
            >
              継続実行を停止する
            </button>
          )}

          <div className={`${CARD} mt-4 overflow-x-auto`}>
            <CountTable caption="実行の内訳" header="結果" counts={latest.runsByStatus} />
          </div>
          <div className={`${CARD} mt-4 overflow-x-auto`}>
            {/* **どの工程で止まりやすいかが、いちばん見たい形である。** */}
            <CountTable
              caption="止まった工程"
              header="工程"
              counts={latest.failuresByStep}
            />
          </div>
        </>
      )}
    </section>
  );
}
