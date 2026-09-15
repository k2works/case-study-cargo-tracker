import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import { ApiError } from '@/shared/api/client';
import { SCENARIOS, fetchSimulationRuns, startSimulation } from './simulationApi';

/**
 * S92 業務シミュレーション（UC23 / US33・US34）。
 *
 * <p><b>実行はここから始める。</b> 識別子を握って API を叩く形にすると、
 * 一覧から辿れない欠陥を踏まない（IT15 の Try T2）。</p>
 *
 * <p><b>実データに紛れる貨物を作る操作である。</b> 無効な環境では断られ、
 * その理由をそのまま出す——「失敗しました」では、設定の問題だと分からない。</p>
 */
export function SimulationListPage() {
  const client = useQueryClient();
  const navigate = useNavigate();
  const [scenario, setScenario] = useState<string>(SCENARIOS[0]);

  const { data, isPending, isError } = useQuery({
    queryKey: ['simulation-runs'],
    queryFn: fetchSimulationRuns,
    // 走っているあいだ工程が増えるので、開いたままでも進みが見える。
    refetchInterval: 3000,
  });

  const start = useMutation({
    mutationFn: () => startSimulation(scenario),
    onSuccess: (started) => {
      client.invalidateQueries({ queryKey: ['simulation-runs'] });
      navigate(`/admin/simulations/${started.runId}`);
    },
  });

  const items = data?.state === 'ready' ? data.value.items : [];
  const refusal = start.error instanceof ApiError ? start.error.body.message : null;

  return (
    <section>
      <h1 className={PAGE_TITLE}>業務シミュレーション</h1>
      <p className="mt-1 text-sm text-gray-600">
        予約から精算までを<b>実際の API で順に流します</b>。作られる荷主・貨物・請求には<b>シミュレーション由来の印</b>が付き、業務の一覧には出ません。
      </p>
      {/* **S94 への入口はここだけ。** 統計は「いま何が起きているか」で、この一覧は
          「何を流したか」——混ぜると一覧の目的がぼやける（注 N6）。 */}
      <p className="mt-2 text-sm">
        <Link className={LINK} to="/admin/simulations/schedule">
          継続実行と統計を見る
        </Link>
      </p>

      <div className={`${CARD} mt-4 flex flex-wrap items-end gap-3 p-4`}>
        <label className="text-sm">
          <span className={LABEL}>シナリオ</span>
          <select
            className={FIELD}
            value={scenario}
            onChange={(event) => setScenario(event.target.value)}
          >
            {SCENARIOS.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <button
          className={BUTTON_PRIMARY}
          type="button"
          disabled={start.isPending}
          onClick={() => start.mutate()}
        >
          実行する
        </button>
        {start.isPending && <output className={NOTICE}>実行を始めています…</output>}
      </div>

      {/* **断りの理由をそのまま出す。** 本番で無効なのか、同じシナリオが
          走っているのかで、次にすることが違う。 */}
      {refusal && (
        <p role="alert" className={`${ALERT} mt-4`}>
          {refusal}
        </p>
      )}

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          実行の一覧を取得できませんでした
        </p>
      )}
      {data?.state === 'ready' && items.length === 0 && (
        <p className="mt-4 text-sm text-gray-600">まだ実行していません。</p>
      )}

      {items.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>実行（{items.length} 件・新しい順）</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>シナリオ</th>
                <th scope="col" className={TH}>状態</th>
                <th scope="col" className={TH}>進み</th>
                <th scope="col" className={TH}>開始</th>
                <th scope="col" className={TH}>終了</th>
                <th scope="col" className={TH}>実行した人</th>
                <th scope="col" className={TH}>結果</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.runId}>
                  <td className={TD}>{item.scenarioLabel}</td>
                  <td className={TD}>{item.statusLabel}</td>
                  {/* **どこまで進んだかを一覧で読める。** 開かないと分からない
                      形にすると、走っているあいだ何も判断できない。 */}
                  <td className={TD}>
                    {item.succeededSteps} / {item.plannedSteps} 工程
                  </td>
                  <td className={TD}>{formatBusinessDateTime(item.startedAt)}</td>
                  <td className={TD}>
                    {item.finishedAt ? formatBusinessDateTime(item.finishedAt) : '—'}
                  </td>
                  <td className={TD}>{item.startedBy}</td>
                  <td className={TD}>
                    <Link className={LINK} to={`/admin/simulations/${item.runId}`}>
                      工程を見る
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
