import type { BookingView } from '@/features/bookings/api';
import {
  businessLocalToInstant,
  formatBusinessDateTime,
} from '@/shared/api/businessDate';
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
  /** キャンセル（US24）。止めていなければ 3 つとも null。 */
  readonly cancelledAt?: string | null;
  readonly cancelReason?: string | null;
  readonly cancelledBy?: string | null;
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
  // **業務タイムゾーンの一日で切る。** UTC の一日で送ると、日本時間の朝 9 時より
  // 前に出る航海が前日の指定で拾われ、指定した日のものが落ちる。
  return {
    departFrom: from ? businessLocalToInstant(`${from}T00:00`) : undefined,
    // 23:59 の「分」までしか入力欄が無いので、秒はここで最後まで伸ばす。
    departTo: to ? endOfSecond(businessLocalToInstant(`${to}T23:59`)) : undefined,
  };
}

/**
 * その航海で経路を組んだ予約（S34 / US24）。
 *
 * <p>止めても予約側の旅程は自動では戻らない。<b>止める前に</b>誰を巻き込むかを
 * 読めるようにする（IT5 引き継ぎ 2）。読み口は予約側にある（区間を持つのは
 * 予約の投影で、航海側は誰が自分を使っているかを知らない）。</p>
 */
export function fetchAffectedBookings(
  voyageNumber: string,
): Promise<Pending<{ items: AffectedBookingView[] }>> {
  return queryClient(`/booking/bookings/by-voyage/${encodeURIComponent(voyageNumber)}`);
}

/** 巻き込む予約 1 件。止めてよいかの判断に要るのは、どの予約かと、いまどの状態か。 */
export interface AffectedBookingView {
  readonly bookingId: string;
  readonly bookingNumber: string;
  readonly bookingStatus: string;
  readonly routingStatus: string;
}

/** その分の最後の秒まで含める。`...T14:59:00Z` → `...T14:59:59Z`。 */
function endOfSecond(instant: string): string {
  return `${instant.slice(0, 17)}59Z`;
}

export function fetchVoyage(voyageNumber: string): Promise<Pending<VoyageView>> {
  return queryClient(`/routing/voyages/${encodeURIComponent(voyageNumber)}`);
}

/**
 * 航海をキャンセルする（US24 / IT5 R.1）。
 *
 * <p>止めてよいかは集約が見る。ここは理由を運ぶだけ。</p>
 */
export function cancelVoyage(
  voyageNumber: string,
  reason: string,
): Promise<{ voyageNumber: string }> {
  return commandClient(`/routing/voyages/${encodeURIComponent(voyageNumber)}/cancel`, { reason });
}

/**
 * 経路候補（US08）。<b>予約 ID で問い合わせる。</b>
 *
 * <p>条件を画面で組み立てて送らない。組むと、予約の期限を直したのに古い期限で
 * 探すことになる。</p>
 *
 * <p>問い合わせられないときはサーバが 503 を返す（空の候補一覧は返らない）。
 * 「候補が無い」と「探せなかった」を画面が言い分けられるようにするため。</p>
 */
export function fetchRouteCandidates(
  bookingId: string,
): Promise<Pending<RouteCandidatesView>> {
  return queryClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/route-candidates`,
  );
}

/**
 * いま何で絞って探したか（S31 / US10）。
 *
 * <p><b>候補と同じ応答で受け取る。</b> 別の読み口にすると、条件を直した直後に
 * 「古い条件で出した候補」と「新しい条件」が並ぶ瞬間ができる。</p>
 */
export interface RouteConditionView {
  readonly arrivalDeadline: string;
  readonly excludeUnLocodes: readonly string[];
  readonly departFromUnLocode: string | null;
}

/**
 * 条件を調整して再算出できるようにする（US10）。
 *
 * <p><b>条件はサーバが持つ。</b> 画面が組み立てて候補算出へ渡すのではなく、集約に
 * 記録してから読み直す。誰がいつ期限を延ばしたかが残る。</p>
 */
export function adjustRouteSpecification(
  bookingId: string,
  condition: {
    arrivalDeadline: string;
    excludeUnLocodes: readonly string[];
    departFromUnLocode: string | null;
  },
): Promise<{ bookingId: string }> {
  return commandClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/route-specification`,
    condition,
    'PUT',
  );
}

/** 組めないことを営業へ差し戻す（US10 §受入基準 4）。 */
export function requestConditionReview(
  bookingId: string,
  reason: string,
): Promise<{ bookingId: string }> {
  return commandClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/condition-review`,
    { reason },
  );
}

/** 経路候補の一覧。`truncated` は探索の上限で切ったことを表す（ADR-0007）。 */
export interface RouteCandidatesView {
  readonly candidates: readonly RouteCandidateView[];
  readonly truncated: boolean;
  readonly condition: RouteConditionView;
}

/**
 * 経路候補 1 件。
 *
 * <p><b>費用の欄は無い。</b> 料金表は US21（IT13）が正典で、現時点で存在しない
 * （US08 §受入基準 3 の未達）。</p>
 */
export interface RouteCandidateView {
  readonly legs: readonly RouteLegView[];
  readonly transitDays: number;
  readonly direct: boolean;
}

export interface RouteLegView {
  readonly voyageNumber: string;
  readonly loadUnLocode: string;
  readonly unloadUnLocode: string;
  readonly loadTime: string;
  readonly unloadTime: string;
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
 * 航海の日時。<b>予約側と同じ業務タイムゾーンで読む</b>（S32・S34）。
 *
 * <p>保存は絶対時刻（TIMESTAMPTZ）。IT4 までは UTC を明示して出していたが、旅程
 * （S22）は業務タイムゾーンで出るので、<b>同じ区間の同じ瞬間が画面によって 9 時間
 * 違って見えていた</b>。港のローカル時刻は港の時間帯を持ってから（US15・IT9）。</p>
 */
export function formatVoyageTime(iso: string): string {
  return formatBusinessDateTime(iso);
}
