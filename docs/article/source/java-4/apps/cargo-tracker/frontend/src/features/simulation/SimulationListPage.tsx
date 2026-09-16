import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
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
import type { StartOutcomeView } from './simulationApi';
import { SCENARIOS, fetchSimulationRuns, startSimulations } from './simulationApi';

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
  // **選ぶのは複数。** シナリオ 1 本は連鎖待ちを含めて数分かかるので、
  // 7 種類を順に流すと待ち時間が積み上がる。同じシナリオは実行中 1 本だけ
  // （US33 §受入基準 5）なので、**違うシナリオは同時に流してよい**。
  const [chosen, setChosen] = useState<readonly string[]>([SCENARIOS[0]]);
  const [outcomes, setOutcomes] = useState<readonly StartOutcomeView[]>([]);

  function toggle(name: string) {
    setChosen((current) => (current.includes(name)
      ? current.filter((it) => it !== name)
      : [...current, name]));
  }

  const { data, isPending, isError } = useQuery({
    queryKey: ['simulation-runs'],
    queryFn: fetchSimulationRuns,
    // 走っているあいだ工程が増えるので、開いたままでも進みが見える。
    refetchInterval: 3000,
  });

  const start = useMutation({
    // **1 本でもまとめて流す口を使う。** 経路を 2 つ持つと、断りの出し方が
    // 片方だけ直る。
    mutationFn: () => startSimulations([...chosen].sort(
      (a, b) => SCENARIOS.indexOf(a as never) - SCENARIOS.indexOf(b as never))),
    onSuccess: (started) => {
      client.invalidateQueries({ queryKey: ['simulation-runs'] });
      setOutcomes(started.items);
      // **1 本だけ始まったときは、その結果へ移る**（これまでと同じ体験）。
      // 複数流したときは一覧に留まる——移ると、他の実行がどうなったか読めない。
      const only = started.items.length === 1 ? started.items[0] : undefined;
      if (only?.runId != null) {
        navigate(`/admin/simulations/${only.runId}`);
      }
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
        <fieldset className="text-sm">
          <legend className={LABEL}>シナリオ（複数選べます）</legend>
          <div className="mt-1 grid gap-1 sm:grid-cols-2">
            {SCENARIOS.map((name) => (
              <label key={name} className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={chosen.includes(name)}
                  onChange={() => toggle(name)}
                />
                <span>{name}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <button
          className={BUTTON_PRIMARY}
          type="button"
          // **1 つも選んでいなければ押せない。** 押せてしまうと、何も起きない
          // のに「流した」と読める。
          disabled={start.isPending || chosen.length === 0}
          onClick={() => start.mutate()}
        >
          {chosen.length > 1 ? `選んだ ${chosen.length} 件を一斉に実行する` : '実行する'}
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

      {/* **断られたシナリオは必ず出す。** 出さないと、選んだのに何も起きて
          いないシナリオが画面から消え、流れたものと区別できない。**1 件でも出す**
          ——「まとめて流したときだけ」にすると、1 つ選んで断られた人には
          何も見えない。 */}
      {outcomes.some((item) => item.runId === null) && (
        <ul role="alert" className={`${ALERT} mt-4 space-y-1`}>
          {outcomes.filter((item) => item.runId === null).map((item) => (
            <li key={item.scenario}>
              <span className="font-medium">{item.scenarioLabel}</span>
              <span className="ml-2">{item.refusalReason}</span>
            </li>
          ))}
        </ul>
      )}

      {/* 始まったものは、そこから開ける（複数流したときは一覧に留まる）。 */}
      {outcomes.filter((item) => item.runId !== null).length > 1 && (
        <ul className={`${CARD} mt-4 space-y-1 p-4 text-sm`} aria-label="始めた実行">
          {outcomes.filter((item) => item.runId !== null).map((item) => (
            <li key={item.scenario}>
              <span className="font-medium">{item.scenarioLabel}</span>
              <Link className={`${LINK} ml-2`} to={`/admin/simulations/${item.runId}`}>
                実行を開く
              </Link>
            </li>
          ))}
        </ul>
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
