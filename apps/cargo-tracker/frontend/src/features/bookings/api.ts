import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED';

export interface BookingView {
  readonly bookingId: string;
  readonly bookingNumber: string;
  readonly shipperId: string;
  /** 鍵を破棄した荷主は null になる（ADR-0003）。 */
  readonly shipperName: string | null;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly arrivalDeadline: string;
  readonly cargoType: CargoType;
  readonly weightKg: string;
  readonly lengthCm: string | null;
  readonly widthCm: string | null;
  readonly heightCm: string | null;
  readonly quantity: number;
  readonly productName: string;
  readonly hazardImoClass: string | null;
  readonly hazardUnNumber: string | null;
  readonly temperatureMinC: string | null;
  readonly temperatureMaxC: string | null;
  readonly bookingStatus: string;
  readonly routingStatus: string;
  readonly bookedAt: string;
  /** 経路設計者へ引き渡した日時（US06）。引き渡していなければ null。 */
  readonly routingRequestedAt: string | null;
  /** 最終更新（US32）。一度も直していなければ null。 */
  readonly updatedAt: string | null;
  readonly updatedBy: string | null;
}

/** 修正の入力（US32）。荷主は変えられない（不変条件 1）。 */
export type UpdateBookingInput = Omit<BookCargoInput, 'shipperId'>;

/** 仮受付の予約情報を修正する（US32）。 */
export function updateBooking(
  bookingId: string,
  input: UpdateBookingInput,
): Promise<{ bookingId: string }> {
  return commandClient(`/booking/bookings/${encodeURIComponent(bookingId)}`, input, 'PUT');
}

export interface BookCargoInput {
  readonly shipperId: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly arrivalDeadline: string;
  readonly cargoType: CargoType;
  readonly weightKg: string;
  readonly lengthCm: string;
  readonly widthCm: string;
  readonly heightCm: string;
  readonly quantity: number;
  readonly productName: string;
  readonly hazardImoClass?: string;
  readonly hazardUnNumber?: string;
  readonly temperatureMinC?: string;
  readonly temperatureMaxC?: string;
}

/**
 * 一覧。
 *
 * <p>既定では精算済とキャンセルを外す（ui_design.md「一覧の既定条件」）。
 * 終わった予約が混ざると、一覧全体が「今日やること」として信用されなくなる。</p>
 */
export function fetchBookings(
  includeFinished = false,
): Promise<Pending<{ items: BookingView[]; total: number }>> {
  return queryClient(
    `/booking/bookings?page=0&size=200&includeFinished=${includeFinished ? 'true' : 'false'}`,
  );
}

export function fetchBooking(bookingId: string): Promise<Pending<BookingView>> {
  return queryClient(`/booking/bookings/${encodeURIComponent(bookingId)}`);
}

/**
 * 修正履歴（US32 §受入基準 4「何を変えたか」）。
 *
 * <p>一度も直していなければ空の一覧が返る。</p>
 */
export function fetchBookingRevisions(
  bookingId: string,
): Promise<Pending<{ items: RevisionView[] }>> {
  return queryClient(`/booking/bookings/${encodeURIComponent(bookingId)}/revisions`);
}

/** 1 回の修正で変わった項目 1 つ。 */
export interface RevisionView {
  readonly updatedAt: string;
  readonly updatedBy: string | null;
  readonly label: string;
  readonly before: string;
  readonly after: string;
}

/**
 * 経路を確定する（US09）。
 *
 * <p><b>候補 ID ではなく旅程そのものを送る。</b> 経路候補はテーブルに持たないので、
 * 選んでから送るまでの間に航海が更新されうる。</p>
 */
export function assignRoute(
  bookingId: string,
  legs: readonly AssignRouteLeg[],
): Promise<{ bookingId: string }> {
  return commandClient(`/booking/bookings/${encodeURIComponent(bookingId)}/route`, { legs });
}

export interface AssignRouteLeg {
  readonly voyageNumber: string;
  readonly loadUnLocode: string;
  readonly unloadUnLocode: string;
  readonly loadTime: string;
  readonly unloadTime: string;
}

/** 確定した旅程（S22 / US09）。まだ決まっていなければ空。 */
export function fetchBookingItinerary(
  bookingId: string,
): Promise<Pending<{ legs: ItineraryLegView[] }>> {
  return queryClient(`/booking/bookings/${encodeURIComponent(bookingId)}/itinerary`);
}

export interface ItineraryLegView {
  readonly legSeq: number;
  readonly voyageNumber: string;
  readonly loadUnLocode: string;
  readonly unloadUnLocode: string;
  readonly loadAt: string;
  readonly unloadAt: string;
}

export function bookCargo(input: BookCargoInput): Promise<{ bookingId: string }> {
  return commandClient('/booking/bookings', input);
}

/** 経路設計の「今日の作業」に出す件数。 */
export function fetchBookingSummary(): Promise<
  Pending<{ preliminary: number; routingWorklist: number }>
> {
  return queryClient('/booking/bookings/summary');
}

/**
 * 状態の呼び名（ui_design.md「付録：ステータスバッジ」）。
 *
 * <p>列挙名のまま見せない。知らない値はそのまま出す。空にすると、状態を足して
 * ラベルを書き忘れたときに欄が消え、「状態の無い予約」に見える。</p>
 */
const BOOKING_STATUS_LABELS: Record<string, string> = {
  PRELIMINARY: '仮受付',
  ROUTE_PROPOSED: '経路提案中',
  ROUTE_NOTIFIED: '通知済み',
  CONFIRMED: '確定',
  TRACKING_ISSUED: '追跡番号発行済み',
  IN_TRANSIT: '輸送中',
  DELIVERED: '引取済',
  SETTLED: '精算済',
  CANCELLED: 'キャンセル',
};

export function bookingStatusLabel(status: string): string {
  return BOOKING_STATUS_LABELS[status] ?? status;
}

const CARGO_TYPE_LABELS: Record<string, string> = {
  GENERAL: '一般',
  HAZARDOUS: '危険物',
  REFRIGERATED: '冷凍・冷蔵',
};

export function cargoTypeLabel(cargoType: string): string {
  return CARGO_TYPE_LABELS[cargoType] ?? cargoType;
}
