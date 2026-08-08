package com.example.cargotracker.handling.domain.model;

/**
 * 荷役作業で読み取った追跡番号（Handling Context 固有の型）。
 *
 * <p>Booking の {@code BookingTrackingNumber} も Tracking の {@code TrackingNumber} も
 * 参照しない（{@code domain-model.md} のコンテキスト分離設計。{@link HandlingVoyageNumber}
 * と同じ形）。
 *
 * <p><strong>これは予約への参照ではなく、作業そのものの事実である。</strong>
 * 「そのとき何を読み取ったか」を残す。誤って別の貨物の番号を読み取った場合、
 * <strong>誤った番号がそのまま残るほうが追跡できる</strong>。予約 ID から
 * 逆算して表示すると、誤読の痕跡が消える。
 *
 * <p><strong>形は検査しない。</strong> 書式（{@code TRK-YYYYMMDD-NNNN}）の検査は
 * 画面が行い、存在するかどうかは ACL が確かめる。ここで形を作り変えると、
 * 誤読を「正しそうな番号」に化けさせてしまう。
 *
 * @param value 読み取った追跡番号
 */
public record ScannedTrackingNumber(String value) {

    public ScannedTrackingNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("読み取った追跡番号は必須です");
        }
        value = value.strip();
    }
}
