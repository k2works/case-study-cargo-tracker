package com.example.bookingms.interfaces.rest;

import java.util.List;

/**
 * 貨物予約の一覧。
 *
 * @param bookings 上限で切った一覧（新しい順）
 * @param totalCount 絞り込み条件に合う総件数
 * @param limit 適用した上限
 * @param truncated 上限で切られているか。黙って切ると「全件見た」と受け取られる
 */
public record BookingListResponse(
        List<BookingResponse> bookings, long totalCount, int limit, boolean truncated) {
}
