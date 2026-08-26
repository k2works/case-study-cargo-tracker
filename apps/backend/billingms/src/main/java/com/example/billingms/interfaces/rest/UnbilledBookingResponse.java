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
 * @param lastHandlingAt <strong>最後に荷役があった日時</strong>（IT11 レビュー 中）。
 *        引取の日時とは限らない——キャンセルされた予約は引き取っていないが、
 *        途中まで運ばれていれば荷役の記録を持つ。<strong>一覧の並びはこの値で決まる</strong>
 *        ので、名前と中身を揃えないと「引取日時」で並んでいるように読まれる
 * @param misrouted 誤配の記録があるか（21-6 の根拠）
 * @param cancelled キャンセルされた予約か（US30-9）
 */
public record UnbilledBookingResponse(
        String bookingId,
        String shipperName,
        String shipperType,
        String originName,
        String destinationName,
        Instant lastHandlingAt,
        boolean misrouted,
        boolean cancelled) {

    public static UnbilledBookingResponse from(BillableCargoSnapshot snapshot) {
        return new UnbilledBookingResponse(
                snapshot.bookingId(),
                snapshot.shipperName(),
                snapshot.corporate() ? "CORPORATE" : "INDIVIDUAL",
                snapshot.originName(),
                snapshot.destinationName(),
                // **最後に荷役があった日時をそのまま返す。** 一覧の並びもこの値で決まる
                // ——名前と中身を揃えないと「引取日時」で並んでいるように読まれる
                snapshot.claimedAt(),
                snapshot.misroute() != null,
                snapshot.cancellation() != null);
    }
}
