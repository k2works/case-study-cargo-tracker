import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/** 実行の概要（S92 / US34 §受入基準 4）。 */
export interface RunSummaryView {
  readonly runId: string;
  readonly scenarioLabel: string;
  readonly status: string;
  readonly statusLabel: string;
  readonly startedAt: string;
  readonly finishedAt: string | null;
  readonly startedBy: string;
  /** 成功した工程数 / 予定の工程数。**どこまで進んだかを一覧から読む**。 */
  readonly succeededSteps: number;
  readonly plannedSteps: number;
}

/** 工程 1 件（S93 / US34 §受入基準 1・2・5）。 */
export interface StepView {
  readonly stepNo: number;
  readonly kind: string;
  readonly kindLabel: string;
  readonly outcome: string;
  readonly outcomeLabel: string;
  readonly elapsedMs: number | null;
  /** その工程が生成した識別子。**ここから業務画面へ行ける**（§受入基準 5）。 */
  readonly producedId: string | null;
  readonly failureStatus: number | null;
  readonly failureMessage: string | null;
  readonly occurredAt: string;
}

/** 実行の詳細（S93）。 */
export interface RunView {
  readonly runId: string;
  readonly scenario: string;
  readonly scenarioLabel: string;
  readonly status: string;
  readonly statusLabel: string;
  readonly seed: number | null;
  readonly startedAt: string;
  readonly finishedAt: string | null;
  readonly startedBy: string;
  readonly steps: readonly StepView[];
}

/**
 * 選べるシナリオ。
 *
 * **呼び名はサーバの列挙と同じ文字列**——実行の要求はこの呼び名で送り、
 * サーバは知らないものを断る（打ち間違いを「工程 0 件で成功」にしない）。
 */
export const SCENARIOS = ['一般貨物の標準輸送', '便が通わない港への輸送'] as const;

/** 実行の一覧（S92）。<b>新しい順</b>。 */
export function fetchSimulationRuns(): Promise<Pending<{ items: RunSummaryView[] }>> {
  return queryClient<{ items: RunSummaryView[] }>('/simulation/runs');
}

/** 実行 1 件（S93）。 */
export function fetchSimulationRun(runId: string): Promise<Pending<RunView>> {
  return queryClient<RunView>(`/simulation/runs/${encodeURIComponent(runId)}`);
}

/** シナリオを実行する（US33）。<b>識別子が返る</b>——画面はその結果へ移る。 */
export async function startSimulation(scenario: string): Promise<{ runId: string }> {
  return commandClient<{ runId: string }>('/simulation/runs', { scenario });
}
