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
