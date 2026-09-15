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
  /**
   * 連鎖の結果が読めるようになるまで待った時間（IT16 のレビュー N8）。
   *
   * **所要時間に足し合わせない。** 足すと「13 工程が数ミリ秒ずつ」と読めて
   * しまい、実際に時間を使っている場所が見えない。
   */
  readonly waitedMs: number | null;
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
  /**
   * 予定の工程（IT16 のレビュー N4）。
   *
   * **記録済みの工程だけでは「進んでいるのか固まったのか」が分からない。**
   * 連鎖待ちは 1 工程あたり最大 30 秒あり、そのあいだ画面には何も増えない。
   */
  readonly plannedSteps: readonly PlannedStepView[];
}

/** 予定の工程 1 件（S93）。 */
export interface PlannedStepView {
  readonly stepNo: number;
  readonly kind: string;
  readonly kindLabel: string;
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

/** 区分ごとの件数（S94 / US36 §受入基準 8）。 */
export interface CountView {
  readonly code: string;
  readonly label: string;
  readonly count: number;
}

/**
 * 継続実行のいまと統計（S94 / US36）。
 *
 * **種が読める**（§3）。読めないと、同じ並びをもう一度流せない。
 */
export interface ScheduleView {
  readonly scheduleId: string;
  readonly seed: number;
  readonly intervalSeconds: number;
  readonly maxConcurrent: number;
  readonly exceptionRatio: number;
  readonly status: string;
  readonly statusLabel: string;
  readonly startedBy: string;
  readonly startedAt: string;
  readonly stoppedAt: string | null;
  readonly runningNow: number;
  readonly runsByStatus: readonly CountView[];
  readonly failuresByStep: readonly CountView[];
}

/**
 * いまの稼働（S94）。
 *
 * <p>動いていなければサーバは 204 を返す。**「見つかりません」の赤にしない**
 * ——動いていないのは正常な状態である。</p>
 */
export function fetchSimulationSchedule(): Promise<Pending<ScheduleView | null>> {
  return queryClient('/simulation/schedule');
}

/** 継続実行を始める（種は任意）。 */
export async function startSimulationSchedule(
  seed: number | null,
): Promise<{ scheduleId: string }> {
  return commandClient('/simulation/schedule', { seed });
}

/** 継続実行を止める。<b>すぐには止まらない</b>（走っている実行は最後まで終える）。 */
export async function stopSimulationSchedule(): Promise<void> {
  await commandClient('/simulation/schedule', undefined, 'DELETE');
}
