package com.example.cargotracker.booking.application.internal.queryservices;

import java.util.UUID;

/**
 * 予約一覧の絞り込み条件（US04 / US34）。
 *
 * <p><strong>`shipperId` は利用者が指定する条件ではない。</strong> ログインしている荷主に
 * 紐づく値であり、画面から変えられない。ほかの 4 つ（出発地・目的地・状態・追跡番号）とは
 * <strong>性質が違うが、同じ SQL の WHERE 句に入る</strong>ため、ひと組にして渡す。
 *
 * <p><strong>絞り込みは SQL で行う。</strong> 読み出してから画面側で捨てると、
 * ページングの件数が合わず、**2 ページ目に他社の予約が現れる**。
 *
 * @param origin         出発地。空なら絞らない
 * @param destination    目的地。空なら絞らない
 * @param status         予約状態。空なら絞らない
 * @param trackingNumber 追跡番号（部分一致）。空なら絞らない
 * @param routingStatus  経路の状態（{@code MISROUTED} など）。空なら絞らない。
 *                       <strong>ダッシュボードの誤配カードの行き先である</strong>（C34）
 * @param shipperId      **荷主。{@code null} なら絞らない（社内利用者）**
 */
public record BookingSearchCriteria(
        String origin,
        String destination,
        String status,
        String trackingNumber,
        String routingStatus,
        UUID shipperId) {

    /** 荷主で絞らない条件（社内利用者）。 */
    public static BookingSearchCriteria of(
            String origin, String destination, String status, String trackingNumber) {
        return of(origin, destination, status, trackingNumber, null);
    }

    /** 経路の状態でも絞る条件（C34）。 */
    public static BookingSearchCriteria of(
            String origin, String destination, String status, String trackingNumber,
            String routingStatus) {
        return new BookingSearchCriteria(
                origin, destination, status, trackingNumber, routingStatus, null);
    }

    /** 荷主で絞る条件（US34）。 */
    public BookingSearchCriteria scopedTo(UUID shipperId) {
        return new BookingSearchCriteria(
                origin, destination, status, trackingNumber, routingStatus, shipperId);
    }
}
