package com.example.billingms.interfaces.rest;

import com.example.billingms.application.port.BillableCargoSnapshot;
import java.time.Instant;

/**
 * 料金未算出の予約 1 件（US21-1）。
 *
 * <p><strong>金額は載せない。</strong>一覧の時点では計算していない——計算するのは
 * 開いたときである。載せると、一覧を開くだけで全件の計算が走る。
 *
 * @param bookingId 予約番号
 * @param shipperName 荷主の社名。<strong>経理担当者は社名で探す</strong>
 * @param shipperType 荷主種別。個人には割引の欄を出さない（22-3）
 * @param originName 出発地
 * @param destinationName 目的地
 * @param claimedAt 引取が完了した日時
 * @param misrouted 誤配の記録があるか（21-6 の根拠）
 * @param cancelled キャンセルされた予約か（US30-9）
 */
public record UnbilledBookingResponse(
        String bookingId,
        String shipperName,
        String shipperType,
        String originName,
        String destinationName,
        Instant claimedAt,
        boolean misrouted,
        boolean cancelled) {

    public static UnbilledBookingResponse from(BillableCargoSnapshot snapshot) {
        return new UnbilledBookingResponse(
                snapshot.bookingId(),
                snapshot.shipperName(),
                snapshot.corporate() ? "CORPORATE" : "INDIVIDUAL",
                snapshot.originName(),
                snapshot.destinationName(),
                snapshot.claimedAt(),
                snapshot.misroute() != null,
                snapshot.cancellation() != null);
    }
}
