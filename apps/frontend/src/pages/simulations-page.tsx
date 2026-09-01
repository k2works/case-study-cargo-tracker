import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ContinuousRunPanel } from "../features/simulation/components/continuous-run-panel";
import { ApiError } from "../lib/api-client";
import {
  useSimulationRuns,
  useSimulationScenarios,
  useStartSimulation,
} from "../features/simulation/queries";
import type { SimulationRun } from "../features/simulation/types";
import { formatBusinessDateTime } from "../lib/business-time";

/** シナリオの名前。**画面が ID をそのまま出さない**——押したものと表示が食い違う。 */
const SCENARIO_LABELS: Record<string, string> = {
  "standard-transport": "標準輸送",
  delay: "遅延",
  damage: "破損",
  misroute: "誤配",
  "customs-hold": "税関保留",
  cancellation: "輸送中キャンセル",
};

function scenarioLabel(id: string): string {
  return SCENARIO_LABELS[id] ?? id;
}

/** 状態の見出し。**画面が列挙の名前をそのまま出さない**。 */
const STATUS_LABELS: Record<SimulationRun["status"], string> = {
  RUNNING: "実行中",
  COMPLETED: "完了",
  FAILED: "失敗",
};

/**
 * 業務シミュレーションの実行と履歴（US34・US35）。
 *
 * 予約から精算まで手で追うには 7 ロール分のログインと 20 以上の画面操作が要る。
 * ここから 1 回で通し、どこで切れているかを工程ごとに見る。
 */
export function SimulationsPage() {
  const { data: scenarios } = useSimulationScenarios();
  const { data: runs, isPending, isError } = useSimulationRuns();
  const start = useStartSimulation();

  // **選んだシナリオを覚える。**一覧の先頭に固定すると、例外シナリオを選べない
  const [selectedId, setSelectedId] = useState<string | null>(null);
  /**
   * 統計から来たときの絞り込み（US37-8）。
   *
   * **押した先が絞られていなければ、繋いだ意味が無い。** 「経路割り当てで
   * 12 件落ちている」と分かった管理者は、その 12 件だけを見たい。
   */
  const [searchParams] = useSearchParams();
  const failedStep = searchParams.get("failedStep");
  const scenarioId = selectedId ?? scenarios?.[0]?.id;
  const selected = scenarios?.find((scenario) => scenario.id === scenarioId);
  const totalSteps = selected?.steps.length ?? 0;
  /**
   * その実行のシナリオの工程数。
   *
   * **選択中のシナリオを全行に使わない。** 工程数はシナリオごとに違う
   * （標準輸送 14・誤配 18・キャンセル 11）ため、正常に完了した誤配が
   * 「18 / 14 工程」と出る——毎朝ありもしない障害を追うことになる。
   */
  const shown = (runs ?? []).filter(
    (run) =>
      failedStep === null ||
      run.steps.some((step) => step.step === failedStep && step.outcome === "FAILED"),
  );
  const failedStepLabel =
    failedStep === null
      ? null
      : ((runs ?? [])
          .flatMap((run) => run.steps)
          .find((step) => step.step === failedStep)?.label ?? failedStep);
  const stepsOf = (run: SimulationRun): number =>
    scenarios?.find((scenario) => scenario.id === run.scenarioId)?.steps.length ??
    run.steps.length;
  // 二重実行を断られたとき、実行中の ID を受け取る（US34-5）。
  // 断るだけでは、指示した人はいま何が動いているかを確かめられない
  const runningRunId =
    start.error instanceof ApiError && start.error.status === 409
      ? ((start.error.body as { runningRunId?: string } | undefined)?.runningRunId ?? null)
      : null;

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-gray-900">業務シミュレーション</h1>

      <p className="text-sm text-gray-700">
        {/* 改行を空白と読ませない（日本語は語間を空けない） */}
        {'予約登録から精算までを、'}
        <strong>実際の利用者として本番と同じ API で</strong>
        {'順に実行します。生成した荷主・貨物・請求書は'}
        <code className="mx-1 font-mono">SIM-</code>
        {'の帯で識別され、経理の締めや荷主一覧には出ません。'}
        {'追跡管理者の未解決例外一覧にも出ません。'}
      </p>

      <div className="flex items-center gap-3">
        <label className="text-sm text-gray-700" htmlFor="scenario">
          シナリオ
        </label>
        <select
          className="rounded border border-gray-300 px-2 py-2"
          disabled={start.isPending}
          id="scenario"
          onChange={(event) => setSelectedId(event.target.value)}
          value={scenarioId ?? ""}
        >
          {(scenarios ?? []).map((scenario) => (
            <option key={scenario.id} value={scenario.id}>
              {scenarioLabel(scenario.id)}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-400"
          disabled={!scenarioId || start.isPending}
          onClick={() => scenarioId && start.mutate(scenarioId)}
        >
          {start.isPending ? "実行しています…" : "実行する"}
        </button>
        {selected ? (
          <span className="text-sm text-gray-600">{totalSteps} 工程</span>
        ) : null}
      </div>

      <ContinuousRunPanel />

      {start.isError ? (
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          実行を開始できませんでした。{start.error.message}
          {runningRunId ? (
            <>
              {" "}
              <Link
                className="text-blue-700 underline"
                to={`/admin/simulations/${runningRunId}`}
              >
                実行中の {runningRunId} を見る
              </Link>
            </>
          ) : null}
        </p>
      ) : null}

      {isPending ? <p className="text-gray-600">読み込んでいます…</p> : null}
      {isError ? (
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          実行の履歴を表示できませんでした。時間をおいて再度お試しください。
        </p>
      ) : null}

      {/* **絞っていることを画面に出す**——出さないと「1 件しかない」と読まれる */}
      {failedStepLabel === null ? null : (
        <p className="flex items-center gap-3 rounded border border-gray-200 bg-gray-50 p-3 text-sm text-gray-700">
          <span>
            <strong>{failedStepLabel}</strong>
            {' で止まった実行だけを表示しています。'}
          </span>
          <Link className="text-blue-700 underline" to="/admin/simulations">
            すべて表示する
          </Link>
        </p>
      )}

      {runs && shown.length === 0 ? (
        <p className="rounded border border-gray-200 bg-gray-50 p-4 text-gray-700">
          {failedStepLabel === null
            ? "まだ実行していません。"
            : "その工程で止まった実行はありません。"}
        </p>
      ) : null}

      {runs && shown.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="min-w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 text-left">
                <th className="py-2">実行 ID</th>
                <th>シナリオ</th>
                <th>状態</th>
                <th>進んだ工程</th>
                <th>開始</th>
                <th>実行者</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((run) => (
                <tr key={run.runId} className="border-b">
                  <td className="py-2 font-mono">
                    <Link
                      className="text-blue-700 underline"
                      to={`/admin/simulations/${run.runId}`}
                    >
                      {run.runId}
                    </Link>
                  </td>
                  <td>{scenarioLabel(run.scenarioId)}</td>
                  <td>{STATUS_LABELS[run.status]}</td>
                  {/* **分母を出す。**件数だけでは、どこまで進んだかを読めない */}
                  <td>
                    {run.steps.length} / {stepsOf(run)} 工程
                  </td>
                  <td>{formatBusinessDateTime(run.startedAt)}</td>
                  <td>{run.startedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
