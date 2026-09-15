import { Link, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import {
  ALERT,
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
import { fetchSimulationRun, startSimulation, type StepView } from './simulationApi';

/**
 * 生成した識別子から業務画面への行き先（US34 §受入基準 5）。
 *
 * <p><b>工程が行き先を知っている。</b> 識別子の見た目で当てると、書式を変えた
 * ときに黙って行き先が消える。<b>行き先の無い識別子は素のまま出す</b>——
 * 荷主 ID のように、開く画面が無いものもある。</p>
 */
function destinationOf(step: StepView): string | null {
  if (!step.producedId) {
    return null;
  }
  switch (step.kind) {
    case 'REGISTER_BOOKING':
      return `/bookings/${step.producedId}`;
    // **一覧ではなく詳細へ送る。** 一覧は絞り込みの引数を読まないうえ、
    // 請求一覧はシミュレーション由来を既定で外すので、飛んだ先に
    // その請求書は**絶対に出ない**（IT16 のレビュー 高）。
    case 'ISSUE_TRACKING_NUMBER':
      return `/tracking/${encodeURIComponent(step.producedId)}`;
    case 'CALCULATE_INVOICE':
      return `/invoices/${encodeURIComponent(step.producedId)}`;
    default:
      return null;
  }
}

/**
 * 止まった工程から、次に取れる行動への行き先（IT16 のレビュー N5）。
 *
 * <p><b>「失敗しました」で終わらせない。</b> マニュアルには次の行動が書いてあるが、
 * 画面には何も無かった——止まった人はそこで手が止まる。原因の種類ごとに
 * 「そこで何を確かめるか」の画面へ送る。</p>
 *
 * <p><b>行き先が無い失敗もある。</b> 分からないものに当てずっぽうのリンクを
 * 出すと、開いた先で何もできず信用を失う。</p>
 */
function recoveryOf(step: StepView): { readonly label: string; readonly to: string } | null {
  if (step.outcome !== 'FAILED') {
    return null;
  }
  const message = step.failureMessage ?? '';
  // 連鎖が追いつかない＝投影が止まっている疑い。退避したイベント（S91）を見る。
  if (message.includes('読めるようになりませんでした')) {
    return { label: '退避したイベントを見る', to: '/admin/dead-letters' };
  }
  // 経路が組めない＝便が無い。航海スケジュール（S32）を見る。
  if (step.kind === 'ASSIGN_ROUTE' || step.kind === 'FIND_ROUTE_CANDIDATES') {
    return { label: '航海スケジュールを見る', to: '/voyages' };
  }
  return null;
}

/**
 * S93 実行結果（US34）。
 *
 * <p><b>止まった工程とその理由を出す。</b> どこまで進んだかを追えることが
 * このストーリーの目的で、<b>それまでに作られた業務データは取り消さない</b>。</p>
 */
export function SimulationRunPage() {
  const { runId = '' } = useParams();
  const navigate = useNavigate();
  const [rerunError, setRerunError] = useState<string | null>(null);
  const rerun = useMutation({
    mutationFn: (scenario: string) => startSimulation(scenario),
    onSuccess: (started) => {
      setRerunError(null);
      void navigate(`/admin/simulations/${started.runId}`);
    },
    // **断りをそのまま出す。** 実行中なら「実行中です」と言われる——
    // 黙って何も起きないより、断りが読めるほうが次の手が決まる。
    onError: (error: Error) => setRerunError(error.message),
  });
  const { data, isPending, isError } = useQuery({
    queryKey: ['simulation-run', runId],
    queryFn: () => fetchSimulationRun(runId),
    refetchInterval: (query) =>
      query.state.data?.state === 'ready' && query.state.data.value.finishedAt ? false : 2000,
  });

  const run = data?.state === 'ready' ? data.value : null;

  return (
    <section>
      <Link className={LINK} to="/admin/simulations">
        ← 業務シミュレーション
      </Link>
      <h1 className={`${PAGE_TITLE} mt-2`}>実行結果</h1>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          この実行は見つかりませんでした
        </p>
      )}

      {run && (
        <>
          <dl className={`${CARD} mt-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4`}>
            <div>
              <dt className="text-gray-600">シナリオ</dt>
              <dd className="font-medium">{run.scenarioLabel}</dd>
            </div>
            <div>
              <dt className="text-gray-600">状態</dt>
              <dd className="font-medium">{run.statusLabel}</dd>
            </div>
            <div>
              <dt className="text-gray-600">開始</dt>
              <dd className="font-medium">{formatBusinessDateTime(run.startedAt)}</dd>
            </div>
            <div>
              <dt className="text-gray-600">終了</dt>
              <dd className="font-medium">
                {run.finishedAt ? formatBusinessDateTime(run.finishedAt) : '実行中'}
              </dd>
            </div>
          </dl>

          <div className={`${CARD} mt-4 overflow-x-auto`}>
            <table className={TABLE}>
              <caption className={TABLE_CAPTION}>
                工程（{run.steps.length} / {run.plannedSteps.length} 件）
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH}>#</th>
                  <th scope="col" className={TH}>工程</th>
                  <th scope="col" className={TH}>結果</th>
                  <th scope="col" className={TH}>呼び出し</th>
                  <th scope="col" className={TH}>連鎖待ち</th>
                  <th scope="col" className={TH}>作られたもの</th>
                  <th scope="col" className={TH}>止まった理由</th>
                </tr>
              </thead>
              <tbody>
                {/* **予定を並べる**（IT16 のレビュー N4）。記録済みだけを出すと、
                    連鎖待ちの 30 秒のあいだ画面が何も変わらず、「進んでいるのか
                    固まったのか」が読めない。 */}
                {run.plannedSteps.map((planned) => {
                  const step = run.steps.find((recorded) => recorded.stepNo === planned.stepNo);
                  if (step === undefined) {
                    return (
                      <tr key={planned.stepNo} className="text-gray-400">
                        <td className={TD}>{planned.stepNo}</td>
                        <td className={TD}>{planned.kindLabel}</td>
                        <td className={TD}>これから</td>
                        <td className={TD}>—</td>
                        <td className={TD}>—</td>
                        <td className={TD}>—</td>
                        <td className={TD}>—</td>
                      </tr>
                    );
                  }
                  const destination = destinationOf(step);
                  const recovery = recoveryOf(step);
                  return (
                    <tr key={planned.stepNo}>
                      <td className={TD}>{step.stepNo}</td>
                      <td className={TD}>{step.kindLabel}</td>
                      <td className={TD}>{step.outcomeLabel}</td>
                      <td className={TD}>
                        {step.elapsedMs === null ? '—' : `${step.elapsedMs} ミリ秒`}
                      </td>
                      <td className={TD}>
                        {step.waitedMs === null || step.waitedMs === 0
                          ? '—'
                          : `${step.waitedMs} ミリ秒`}
                      </td>
                      <td className={TD}>
                        {step.producedId === null && '—'}
                        {step.producedId !== null && destination === null && step.producedId}
                        {step.producedId !== null && destination !== null && (
                          <Link className={LINK} to={destination}>
                            {step.producedId}
                          </Link>
                        )}
                      </td>
                      <td className={TD}>
                        {step.failureMessage
                          ? `${step.failureStatus ?? ''} ${step.failureMessage}`.trim()
                          : '—'}
                        {recovery !== null && (
                          <>
                            {' '}
                            <Link className={LINK} to={recovery.to}>
                              {recovery.label}
                            </Link>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* **もう一度流せる**（IT16 のレビュー N6）。切り分けは「直す → 流す」を
              何度も回す作業で、一覧へ戻ってシナリオを選び直すのは毎回同じ手間。 */}
          <p className="mt-4 flex items-center gap-3 text-sm">
            <button
              type="button"
              className={LINK}
              disabled={rerun.isPending}
              onClick={() => rerun.mutate(run.scenario)}
            >
              同じシナリオをもう一度流す
            </button>
            {rerunError !== null && (
              <span role="alert" className={ALERT}>{rerunError}</span>
            )}
          </p>
        </>
      )}
    </section>
  );
}
