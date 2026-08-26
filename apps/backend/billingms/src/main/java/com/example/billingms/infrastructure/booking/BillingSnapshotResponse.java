package com.example.billingms.infrastructure.booking;

import com.example.billingms.application.port.BillableCargoSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * bookingms からの応答（[ADR-027] 決定 7）。
 *
 * <p><strong>ここが境界である。</strong>相手の言葉のまま受け、こちらの言葉
 * （{@link BillableCargoSnapshot}）へ移す。
 *
 * <p><strong>知らない項目は無視する</strong>（{@code @JsonIgnoreProperties}）。相手が項目を
 * 足しただけでこちらが落ちると、bookingms は項目 1 つ足すのに billingms の
 * デプロイを待つことになる。
 *
 * @param bookingId 予約番号
 * @param bookingStatus 予約の状態
 * @param shipperId 荷主 ID
 * @param shipperName 荷主の社名
 * @param shipperType 荷主種別（{@code CORPORATE} / {@code INDIVIDUAL}）
 * @param discountRate 契約割引率（率）。未設定なら {@code null}
 * @param weightKg 重量
 * @param cargoType 貨物種別
 * @param originName 出発地
 * @param destinationName 目的地
 * @param legCount 区間数
 * @param claimedAt 引取が完了した日時
 * @param misroute 誤配の記録
 * @param cancellation キャンセルの記録
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingSnapshotResponse(
        String bookingId,
        String bookingStatus,
        String shipperId,
        String shipperName,
        String shipperType,
        BigDecimal discountRate,
        BigDecimal weightKg,
        String cargoType,
        String originName,
        String destinationName,
        int legCount,
        Instant claimedAt,
        MisrouteResponse misroute,
        CancellationResponse cancellation) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MisrouteResponse(Instant at, String locationUnLocode, String locationName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancellationResponse(String bookingStatusAtRequest, Instant requestedAt) {
    }

    /**
     * こちらの言葉へ移す。
     *
     * <p><strong>法人かどうかはここで判定する。</strong>文字列の突き合わせを
     * ドメインへ持ち込まない——{@code "CORPORATE"} という綴りは相手の都合である。
     */
    public BillableCargoSnapshot toSnapshot() {
        return new BillableCargoSnapshot(bookingId, bookingStatus, shipperId, shipperName,
                "CORPORATE".equals(shipperType), discountRate, weightKg, cargoType,
                originName, destinationName, legCount, claimedAt,
                misroute == null ? null : new BillableCargoSnapshot.Misroute(
                        misroute.at(), misroute.locationUnLocode(), misroute.locationName()),
                cancellation == null ? null : new BillableCargoSnapshot.Cancellation(
                        cancellation.bookingStatusAtRequest(), cancellation.requestedAt()));
    }
}
