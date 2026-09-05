import type { BookingView } from '@/features/bookings/api';
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
  /** 最終更新（US25）。一度も更新していなければ null。 */
  readonly updatedAt: string | null;
  readonly updatedBy: string | null;
}

/** 更新前後の差分 1 件（US25 §受入基準 2）。サーバが出す。 */
export interface FieldChange {
  readonly label: string;
  readonly before: string;
  readonly after: string;
}

/** 更新の入力。航海番号は経路が持つので本文に入れない（不変条件 1）。 */
export interface UpdateVoyageInput {
  readonly carrierCode: string;
  readonly carrierName: string;
  readonly vesselName: string;
  readonly movements: readonly MovementInput[];
  readonly acceptedCargoTypes: readonly AcceptedCargoType[];
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
  criteria: VoyageSearchInput = {},
): Promise<Pending<{ items: VoyageView[]; total: number }>> {
  const query = new URLSearchParams({
    page: '0',
    size: '200',
    includeFinished: includeFinished ? 'true' : 'false',
  });
  // 空の条件は送らない。空文字を送ると「その値で絞る」と読める経路が増える
  // （解釈はサーバの VoyageSearchCriteria が正典なので、ここでは送らないだけ）。
  for (const [key, value] of Object.entries(criteria)) {
    if (value) {
      query.set(key, value);
    }
  }
  return queryClient(`/routing/voyages?${query.toString()}`);
}

/** 検索条件（US07）。解釈の正典はサーバの VoyageSearchCriteria。 */
export interface VoyageSearchInput {
  readonly departure?: string;
  readonly arrival?: string;
  /** 絶対時刻（ISO）。画面は日付で入力し、送る前に時刻へ広げる。 */
  readonly departFrom?: string;
  readonly departTo?: string;
  readonly cargoType?: string;
}

/**
 * 出発日の入力（YYYY-MM-DD）を絶対時刻に広げる。
 *
 * <p>終了日はその日の終わりまで含める。日付の 00:00 で切ると、指定した日に出る
 * 航海が結果から落ちる（利用者は「その日まで」と読む）。</p>
 */
export function departurePeriod(from: string, to: string): {
  departFrom?: string;
  departTo?: string;
} {
  return {
    departFrom: from ? `${from}T00:00:00Z` : undefined,
    departTo: to ? `${to}T23:59:59Z` : undefined,
  };
}

export function fetchVoyage(voyageNumber: string): Promise<Pending<VoyageView>> {
  return queryClient(`/routing/voyages/${encodeURIComponent(voyageNumber)}`);
}

export function registerVoyage(input: RegisterVoyageInput): Promise<{ voyageNumber: string }> {
  return commandClient('/routing/voyages', input);
}

/**
 * 更新前後の差分を問い合わせる（US25 §受入基準 2）。
 *
 * <p><b>差分はサーバが出す。</b> 画面で 2 つの値を並べて if を積み上げると、
 * 航海に属性が増えるたびに比べ忘れが生まれる。</p>
 */
export function diffVoyage(
  voyageNumber: string,
  input: UpdateVoyageInput,
): Promise<VoyageDiff> {
  return commandClient<VoyageDiff>(
    `/routing/voyages/${encodeURIComponent(voyageNumber)}/diff`,
    input,
  );
}

/**
 * 差分の応答。投影がまだなら比べる相手が無いので、変更 0 件ではなく案内が返る。
 * 0 件と同じ形にすると、「反映待ち」が「変更なし」に見える。
 */
export type VoyageDiff =
  | { readonly voyageNumber: string; readonly changes: FieldChange[] }
  | { readonly voyageNumber: string; readonly message: string };

/** スケジュールを更新する（US25）。 */
export function updateVoyage(
  voyageNumber: string,
  input: UpdateVoyageInput,
): Promise<{ voyageNumber: string }> {
  return commandClient(`/routing/voyages/${encodeURIComponent(voyageNumber)}`, input, 'PUT');
}

/**
 * 経路設計作業一覧（S30）。
 *
 * <p>供給元は予約（bookingms）。routing_read_db に予約の写しは作らない。
 * 作ると Booking の状態と二重管理になる。</p>
 */
export function fetchRoutingWorklist(
  includeRouted = false,
): Promise<Pending<{ items: BookingView[]; total: number }>> {
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
 * 当面は UTC を明示して見せる。港のローカル時刻は港の時間帯を持ってから。
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
