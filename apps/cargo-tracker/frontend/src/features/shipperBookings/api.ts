import { queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/**
 * 自社予約 1 行（S45 / US18）。
 *
 * <p><b>金額・社内メモ・担当者名は入らない。</b> サーバが渡さないので、画面が
 * 間違えても漏れない（`ShipperBookingController`。ui_design.md「S45」）。</p>
 */
export interface ShipperBookingView {
  readonly bookingId: string;
  readonly bookingNumber: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly arrivalDeadline: string;
  readonly productName: string;
  readonly bookingStatus: string;
  /** 追跡番号。**発行前は null** なので、画面は S41 へのリンクを出さない。 */
  readonly trackingNumber: string | null;
}

export interface ShipperBookingListView {
  readonly items: readonly ShipperBookingView[];
  /** 対象の総件数。<b>上限で切れていることを黙らない</b>ために出す。 */
  readonly total: number;
}

/** 旅程の 1 区間。<b>並び順が業務の意味を持つ</b>（積む順）。 */
export interface ShipperItineraryLegView {
  readonly legSeq: number;
  readonly voyageNumber: string;
  readonly loadUnLocode: string;
  readonly unloadUnLocode: string;
  readonly loadAt: string | null;
  readonly unloadAt: string | null;
}

/** 連絡の記録（S46）。<b>担当者名と宛先は入らない</b>。 */
export interface ShipperNotificationView {
  readonly notifiedAt: string;
  readonly summary: string;
}

/** 自社予約の進み具合（S46 / UC10・UC15）。 */
export interface ShipperBookingProgressView {
  readonly bookingId: string;
  readonly bookingNumber: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly arrivalDeadline: string;
  readonly cargoType: string;
  readonly productName: string;
  readonly bookingStatus: string;
  readonly routingStatus: string;
  readonly bookedAt: string;
  readonly routingRequestedAt: string | null;
  readonly lastNotifiedAt: string | null;
  readonly confirmedAt: string | null;
  readonly trackingNumber: string | null;
  readonly trackingIssuedAt: string | null;
  readonly legs: readonly ShipperItineraryLegView[];
  readonly notifications: readonly ShipperNotificationView[];
}

/** S45: 自社予約一覧。<b>絞るのはサーバ</b>（荷主 ID は Gateway が載せる）。 */
export function fetchShipperBookings(
  includeFinished: boolean,
): Promise<Pending<ShipperBookingListView>> {
  return queryClient(
    `/booking/shipper/bookings?includeFinished=${String(includeFinished)}&limit=200`,
  );
}

/** S46: 自社予約の進み具合。他社のものは 404（存在しないものと区別しない）。 */
export function fetchShipperBookingProgress(
  bookingId: string,
): Promise<Pending<ShipperBookingProgressView>> {
  return queryClient(`/booking/shipper/bookings/${encodeURIComponent(bookingId)}`);
}
