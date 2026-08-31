import { Link, useParams } from "react-router-dom";
import { useSimulationRun } from "../features/simulation/queries";
import type { SimulationStep } from "../features/simulation/types";
import { formatBusinessDateTime } from "../lib/business-time";

/** 状態の見出し。画面に対訳表を持たせない方針は一覧と同じ。 */
const STATUS_LABELS: Record<string, string> = {
  RUNNING: "実行中",
  COMPLETED: "完了",
  FAILED: "失敗",
};

/** 追跡番号の形。**この形の識別子だけ、追跡照会へ繋ぐ**。 */
const TRACKING_NUMBER = /^TRK-\d{8}-\d{4}$/;

/**
 * 生成した識別子を、行ける先へ繋ぐ。
 *
 * **繋ぐのは追跡照会だけである。** 予約詳細は営業・経路設計者、精算書は経理にしか
 * 開かれていない。システム管理者が押すと 403 になる先へは繋がない——
 * 気づく手段は次の行動へ繋ぐものであって、行き止まりへ送るものではない。
 */
function Identifier({ step }: Readonly<{ step: SimulationStep }>) {
  if (!step.createdIdentifier) {
    return <span className="text-gray-400">—</span>;
  }
  if (TRACKING_NUMBER.test(step.createdIdentifier)) {
    return (
      <Link
        className="font-mono text-blue-700 underline"
        to={`/tracking/${encodeURIComponent(step.createdIdentifier)}`}
      >
        {step.createdIdentifier}
      </Link>
    );
  }
  return <span className="font-mono">{step.createdIdentifier}</span>;
}

/**
 * 実行 1 件の工程ごとの結果（US35）。
 *
 * 「失敗しました」だけでは、経路候補が 0 件なのか接続先が違うのかを切り分けられない。
 * 所要時間・生成した識別子・失敗理由をそのまま出す。
 */
export function SimulationDetailPage() {
  const { runId } = useParams<{ runId: string }>();
  const { data: run, isPending, isError } = useSimulationRun(runId);

  if (isPending) {
    return <p className="text-gray-600">読み込んでいます…</p>;
  }

  if (isError || !run) {
    return (
      <div className="space-y-4">
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          その実行は表示できませんでした。
        </p>
        <Link className="text-blue-700 underline" to="/admin/simulations">
          実行の一覧に戻る
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-xl font-bold text-gray-900">
          実行 <span className="font-mono">{run.runId}</span>
        </h1>
        <p className="text-sm text-gray-600">
          {STATUS_LABELS[run.status]} ／ 実行者 {run.startedBy} ／ 開始{" "}
          {formatBusinessDateTime(run.startedAt)}
          {run.finishedAt ? ` ／ 終了 ${formatBusinessDateTime(run.finishedAt)}` : ""}
        </p>
      </div>

      {run.failureReason ? (
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          {/* **止まった理由をそのまま出す。**「失敗しました」だけでは切り分けられない */}
          止まりました: {run.failureReason}
        </p>
      ) : null}

      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-gray-300 text-left">
              <th className="py-2">工程</th>
              <th>踏んだロール</th>
              <th>結果</th>
              <th>所要</th>
              <th>生成した識別子</th>
              <th>理由</th>
            </tr>
          </thead>
          <tbody>
            {run.steps.map((step) => (
              <tr key={step.step} className="border-b">
                <td className="py-2">{step.label}</td>
                <td className="font-mono text-xs">{step.role}</td>
                <td>{step.outcome === "SUCCEEDED" ? "成功" : "失敗"}</td>
                <td>{step.elapsedMs} ms</td>
                <td>
                  <Identifier step={step} />
                </td>
                <td className="text-red-700">{step.failureReason ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Link className="text-blue-700 underline" to="/admin/simulations">
        実行の一覧に戻る
      </Link>
    </div>
  );
}
