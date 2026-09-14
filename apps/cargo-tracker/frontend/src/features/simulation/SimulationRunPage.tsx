import { Link, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
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
import { fetchSimulationRun, type StepView } from './simulationApi';

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
    case 'ISSUE_TRACKING_NUMBER':
      return `/tracking?q=${encodeURIComponent(step.producedId)}`;
    case 'CALCULATE_INVOICE':
      return `/invoices?q=${encodeURIComponent(step.producedId)}`;
    default:
      return null;
  }
}

/**
 * S93 実行結果（US34）。
 *
 * <p><b>止まった工程とその理由を出す。</b> どこまで進んだかを追えることが
 * このストーリーの目的で、<b>それまでに作られた業務データは取り消さない</b>。</p>
 */
export function SimulationRunPage() {
  const { runId = '' } = useParams();
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
              <caption className={TABLE_CAPTION}>工程（{run.steps.length} 件）</caption>
              <thead>
                <tr>
                  <th scope="col" className={TH}>#</th>
                  <th scope="col" className={TH}>工程</th>
                  <th scope="col" className={TH}>結果</th>
                  <th scope="col" className={TH}>所要</th>
                  <th scope="col" className={TH}>作られたもの</th>
                  <th scope="col" className={TH}>止まった理由</th>
                </tr>
              </thead>
              <tbody>
                {run.steps.map((step) => {
                  const destination = destinationOf(step);
                  return (
                    <tr key={step.stepNo}>
                      <td className={TD}>{step.stepNo}</td>
                      <td className={TD}>{step.kindLabel}</td>
                      <td className={TD}>{step.outcomeLabel}</td>
                      <td className={TD}>
                        {step.elapsedMs === null ? '—' : `${step.elapsedMs} ミリ秒`}
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
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {run.steps.length === 0 && (
            <p className="mt-4 text-sm text-gray-600">
              まだ工程が記録されていません（実行を始めたところです）。
            </p>
          )}
        </>
      )}
    </section>
  );
}
