import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/**
 * 航海が受け入れる貨物種別。
 *
 * <p>予約側の CargoType とは別の型。Booking では「その貨物が何か」、Routing では
 * 「その航海が何を受け入れるか」で、値が増える理由も別になる（domain-model.md）。</p>
 */
export type AcceptedCargoType = 'GENERAL' | 'HAZARDOUS' | 'REEFER';

export interface MovementView {
  readonly movementSeq: number;
  readonly departureUnLocode: string;
  readonly arrivalUnLocode: string;
  readonly departureAt: string;
  readonly arrivalAt: string;
}

export interface VoyageView {
  readonly voyageNumber: string;
  readonly carrierCode: string;
  readonly carrierName: string;
  readonly vesselName: string;
  readonly departureUnLocode: string;
  readonly arrivalUnLocode: string;
  readonly departureAt: string;
  readonly arrivalAt: string;
  readonly cancelled: boolean;
  readonly acceptedCargoTypes: readonly AcceptedCargoType[];
  readonly movements: readonly MovementView[];
}

export interface MovementInput {
  readonly departureUnLocode: string;
  readonly arrivalUnLocode: string;
  readonly departureAt: string;
  readonly arrivalAt: string;
}

export interface RegisterVoyageInput {
  readonly voyageNumber: string;
  readonly carrierCode: string;
  readonly carrierName: string;
  readonly vesselName: string;
  readonly movements: readonly MovementInput[];
  readonly acceptedCargoTypes: readonly AcceptedCargoType[];
}

/**
 * 航海一覧（S32）。
 *
 * <p>既定では出港済みとキャンセルを外す。出港してしまった便が混ざると、一覧全体が
 * 「これから使える航海」として信用されなくなる（ui_design.md）。</p>
 *
 * <p>{@code cargoType} を渡すと、その種別を受け入れる航海だけに絞る（US05）。</p>
 */
export function fetchVoyages(
  includeFinished = false,
  cargoType?: AcceptedCargoType,
): Promise<Pending<{ items: VoyageView[]; total: number }>> {
  const filter = cargoType ? `&cargoType=${cargoType}` : '';
  return queryClient(
    `/routing/voyages?page=0&size=200&includeFinished=${includeFinished ? 'true' : 'false'}${filter}`,
  );
}

export function fetchVoyage(voyageNumber: string): Promise<Pending<VoyageView>> {
  return queryClient(`/routing/voyages/${encodeURIComponent(voyageNumber)}`);
}

export function registerVoyage(input: RegisterVoyageInput): Promise<{ voyageNumber: string }> {
  return commandClient('/routing/voyages', input);
}

/**
 * 経路設計作業一覧（S30）。
 *
 * <p>供給元は予約（bookingms）。routing_read_db に予約の写しは作らない。
 * 作ると Booking の状態と二重管理になる。</p>
 */
export function fetchRoutingWorklist(
  includeRouted = false,
): Promise<Pending<{ items: unknown[]; total: number }>> {
  return queryClient(
    `/booking/bookings/routing-worklist?page=0&size=200&includeRouted=${includeRouted ? 'true' : 'false'}`,
  );
}

/** 経路設計者に引き渡す（US06）。 */
export function requestRouting(bookingId: string): Promise<{ bookingId: string }> {
  return commandClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/routing-request`,
    {},
  );
}

const ACCEPTED_CARGO_TYPE_LABELS: Record<string, string> = {
  GENERAL: '一般貨物',
  HAZARDOUS: '危険物',
  REEFER: '冷凍・冷蔵貨物',
};

/** 列挙名のまま見せない。知らない値はそのまま出す（欄が消えるより読める）。 */
export function acceptedCargoTypeLabel(cargoType: string): string {
  return ACCEPTED_CARGO_TYPE_LABELS[cargoType] ?? cargoType;
}

/**
 * 港のローカル時刻で見せる（non_functional.md）。
 *
 * <p>保存は絶対時刻（TIMESTAMPTZ）。利用者の居る場所の時刻に揃えると、出港に
 * 間に合うかの判断を誤る。港の時間帯が分かるまでは、どの時刻かを明示して出す。</p>
 */
export function formatVoyageTime(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) {
    return iso;
  }
  return `${at.toISOString().slice(0, 16).replace('T', ' ')} UTC`;
}
