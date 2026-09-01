import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ContinuousRunPanel } from "../features/simulation/components/continuous-run-panel";
import { ApiError } from "../lib/api-client";
import {
  useSimulationRuns,
  useSimulationSessions,
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
  /**
   * 見る日（TD-03）。
   *
   * **直近 50 件だけでは、落ちた実行へ翌朝辿り着けない。**継続実行を一晩回すと、
   * 昨日の失敗は朝には窓の外に落ちている。
   */
  const [date, setDate] = useState("");
  const { data: runs, isPending, isError } = useSimulationRuns(date);
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

      {/* **日で絞れる。**絞れないと、一晩分に押し出された昨日の失敗へ手が届かない */}
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col text-sm">
          <span className="text-gray-700">実行した日</span>
          <input
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
            className="mt-1 rounded border border-gray-300 px-3 py-2"
          />
        </label>
        {date !== "" && (
          <button
            type="button"
            onClick={() => setDate("")}
            className="rounded border border-gray-300 px-3 py-2 text-sm hover:bg-gray-100"
          >
            直近に戻す
          </button>
        )}
      </div>

      <RunTable runs={shown} stepsOf={stepsOf} />

      <PastSessions />

    </div>
  );
}

/**
 * 実行の履歴。
 *
 * **一覧を分けたのは、画面の入口が「実行の指示」と「履歴の閲覧」の 2 つを
 * 抱えていたため**である。行ごとの分母を出す都合で条件が増え、入口そのものが
 * 読みにくくなっていた。
 */
function RunTable({
  runs,
  stepsOf,
}: Readonly<{ runs: SimulationRun[]; stepsOf: (run: SimulationRun) => number }>) {
  if (runs.length === 0) {
    return null;
  }
  return (
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
              {runs.map((run) => (
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
  );
}

/**
 * 過去のセッション（TD-03）。
 *
 * **停止した瞬間に種が画面から消えると、翌朝には再現の手立てが無い。**
 * US37-3 が言う「同じ種を指定すると同じ並びを再現できる」は、その種を読めて
 * 初めて意味を持つ。
 */
function PastSessions() {
  const { data: sessions } = useSimulationSessions();
  if (sessions === undefined || sessions.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby="past-sessions" className="space-y-2">
      <h2 id="past-sessions" className="text-lg font-semibold">
        過去の継続実行
      </h2>
      <p className="text-sm text-gray-600">
        {"種を控えておくと、同じ並びをもう一度流せます。"}
      </p>
      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse text-sm">
          <thead>
            <tr className="border-b text-left">
              <th className="py-2">セッション</th>
              <th>状態</th>
              <th>種</th>
              <th>間隔</th>
              <th>同時実行</th>
              <th>例外の割合</th>
              <th>開始</th>
              <th>停止</th>
            </tr>
          </thead>
          <tbody>
            {sessions.map((session) => (
              <tr key={session.sessionId} className="border-b">
                <td className="py-2 font-mono">{session.sessionId}</td>
                <td>{session.statusLabel}</td>
                {/* **種は控えられる形で出す。**読めなければ再現できない */}
                <td className="font-mono">{session.seed}</td>
                <td>{session.intervalSeconds} 秒</td>
                <td>{session.maxConcurrent} 本</td>
                <td>{Math.round(session.exceptionRatio * 100)}%</td>
                <td>{formatBusinessDateTime(session.startedAt)}</td>
                <td>
                  {session.stoppedAt === null
                    ? "—"
                    : formatBusinessDateTime(session.stoppedAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
