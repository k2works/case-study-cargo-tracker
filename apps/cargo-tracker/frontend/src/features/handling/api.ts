import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/** 荷役の種別（domain-model.md の要素表）。**引取は US16・IT10**。 */
export type HandlingType = 'RECEIVE' | 'LOAD' | 'UNLOAD' | 'CLAIM';

/**
 * 呼び名。<b>正典は要素表</b>で、`handlingTypeLabels.test.ts` が読んで突き合わせる。
 *
 * <p>サーバも履歴に呼び名を載せるが、S50 の選択肢はサーバへ問い合わせずに出す
 * ——航海を選んだ時点で決まっており、1 往復増やす理由がない。</p>
 */
export const HANDLING_TYPE_LABELS: Record<HandlingType, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CLAIM: '引取',
};

/** 本 IT で選べる種別。**引取は US16（IT10）** で、通関と荷受人の確認が要る。 */
export const SELECTABLE_HANDLING_TYPES: readonly HandlingType[] = ['RECEIVE', 'LOAD', 'UNLOAD'];

/** S50 に出す貨物 1 件。 */
export interface CargoOnVoyageView {
  readonly trackingNumber: string;
  readonly bookingId: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly cargoType: string;
  /** すでにこの港で記録済みか。「残り」を数えるのに使う。 */
  readonly handledHere: boolean;
}

/** 予定の旅程の 1 区間。**時刻は持たない**（ADR-0012 決定 4）。 */
export interface LegView {
  readonly voyageNumber: string;
  readonly loadUnLocode: string;
  readonly unloadUnLocode: string;
}

/** S50 の「確認」欄に出す中身。 */
export interface CargoSnapshotView {
  readonly trackingNumber: string;
  readonly bookingId: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly cargoType: string;
  readonly legs: readonly LegView[];
}

/** 荷役履歴の 1 行（S51）。 */
export interface HandlingHistoryItemView {
  readonly activityId: string;
  readonly handlingType: HandlingType;
  readonly handlingTypeLabel: string;
  readonly unLocode: string;
  readonly voyageNumber: string | null;
  readonly offRoute: boolean;
  readonly operator: string;
  readonly completedAt: string;
  readonly voided: boolean;
  readonly voidReason: string | null;
}

/** 荷役履歴（S51）。 */
export interface HandlingHistoryView {
  readonly trackingNumber: string;
  readonly items: readonly HandlingHistoryItemView[];
}

/** この航海がこの港で降ろす貨物（S50 の起点）。 */
export function fetchCargosOnVoyage(
  voyageNumber: string,
  unLocode: string,
): Promise<Pending<{ items: CargoOnVoyageView[] }>> {
  return queryClient(
    `/handling/voyages/${encodeURIComponent(voyageNumber)}/cargos`
    + `?unLocode=${encodeURIComponent(unLocode)}`,
  );
}

/** 貨物 1 件の写し（スキャン後の確認）。見つからなければ ApiError(404)。 */
export function fetchCargoSnapshot(trackingNumber: string): Promise<Pending<CargoSnapshotView>> {
  return queryClient(`/handling/cargos/${encodeURIComponent(trackingNumber)}`);
}

/** 荷役履歴（S51）。 */
export function fetchHandlingHistory(
  trackingNumber: string,
): Promise<Pending<HandlingHistoryView>> {
  return queryClient(`/handling/${encodeURIComponent(trackingNumber)}/activities`);
}

/**
 * 荷役を記録する（US15）。
 *
 * <p><b>冪等キーは画面が作る。</b> サーバが採ると、通信断で再送したときに
 * 別の鍵になって二重に記録される。現場は電波の届かない岸壁で使う。</p>
 */
export function registerHandling(input: {
  readonly activityId: string;
  readonly trackingNumber: string;
  readonly handlingType: HandlingType;
  readonly unLocode: string;
  readonly voyageNumber: string | null;
}): Promise<void> {
  return commandClient('/handling/activities', input);
}

/** 記録を取り消す（不変条件 7）。元の記録は残る。 */
export function voidHandling(activityId: string, reason: string): Promise<void> {
  return commandClient(`/handling/activities/${encodeURIComponent(activityId)}/void`, { reason });
}

/** これから作業する航海と港（S02 荷役）。 */
export interface VoyagePortView {
  readonly voyageNumber: string;
  readonly unLocode: string;
  readonly cargoCount: number;
}

/** これから作業する航海と港の一覧。 */
export function fetchVoyagePorts(): Promise<Pending<{ items: VoyagePortView[] }>> {
  return queryClient('/handling/voyages');
}
