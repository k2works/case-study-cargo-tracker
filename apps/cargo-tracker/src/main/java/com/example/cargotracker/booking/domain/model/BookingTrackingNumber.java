package com.example.cargotracker.booking.domain.model;

import java.util.regex.Pattern;

/**
 * 予約に紐づく追跡番号（US14）。
 *
 * <p><strong>Tracking の {@code TrackingNumber} を参照しない。</strong> 採番と
 * 追跡活動の管理は Tracking Context の仕事であり、Booking が知る必要があるのは
 * 「この予約にはこの番号が付いた」という事実だけである（ADR-005・ArchUnit ルール 4）。
 * {@link Leg} が航海番号を文字列で持つのと同じ理由である。
 *
 * <p>それでも<strong>素の文字列では持たない。</strong> 形式を検査しないまま
 * 画面から受け取ると、空文字や別の番号体系がそのまま貨物に付く。
 *
 * @param value 追跡番号（{@code TRK-YYYYMMDD-NNNN} 形式）
 */
public record BookingTrackingNumber(String value) {

    /** 形式の正典は {@code ui_design.md}「荷役作業登録」の追跡番号欄である。 */
    private static final Pattern FORMAT = Pattern.compile("^TRK-\\d{8}-\\d{4}$");

    public BookingTrackingNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        value = value.strip();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "追跡番号の形式が正しくありません（TRK-YYYYMMDD-NNNN）: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
