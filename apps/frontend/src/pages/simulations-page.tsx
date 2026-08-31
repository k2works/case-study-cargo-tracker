import { Link } from "react-router-dom";
import { ApiError } from "../lib/api-client";
import {
  useSimulationRuns,
  useSimulationScenarios,
  useStartSimulation,
} from "../features/simulation/queries";
import type { SimulationRun } from "../features/simulation/types";
import { formatBusinessDateTime } from "../lib/business-time";

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

  const scenarioId = scenarios?.[0]?.id;
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
      </p>

      <div className="flex items-center gap-3">
        <button
          type="button"
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-400"
          disabled={!scenarioId || start.isPending}
          onClick={() => scenarioId && start.mutate(scenarioId)}
        >
          {start.isPending ? "実行しています…" : "標準輸送シナリオを実行する"}
        </button>
        {scenarios?.[0] ? (
          <span className="text-sm text-gray-600">
            {scenarios[0].steps.length} 工程
          </span>
        ) : null}
      </div>

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

      {runs && runs.length === 0 ? (
        <p className="rounded border border-gray-200 bg-gray-50 p-4 text-gray-700">
          まだ実行していません。
        </p>
      ) : null}

      {runs && runs.length > 0 ? (
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
                  <td>{run.scenarioId}</td>
                  <td>{STATUS_LABELS[run.status]}</td>
                  <td>{run.steps.length} 工程</td>
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
