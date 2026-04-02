package com.example.cargotracker.quote.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ルート候補を表す値オブジェクト。
 */
public final class RouteOption {

    private final List<String> viaLocodes;
    private final int transitDays;
    private final BigDecimal estimatedPrice;
    private final String voyageNumber;

    public RouteOption(List<String> viaLocodes, int transitDays,
                       BigDecimal estimatedPrice, String voyageNumber) {
        if (viaLocodes == null) {
            throw new IllegalArgumentException("経由港リストは null にできません");
        }
        if (transitDays <= 0) {
            throw new IllegalArgumentException("所要日数は 0 より大きくなければなりません");
        }
        if (estimatedPrice == null) {
            throw new IllegalArgumentException("概算料金は null にできません");
        }
        if (estimatedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("概算料金は 0 より大きくなければなりません");
        }
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new IllegalArgumentException("航海番号は null または空にできません");
        }
        this.viaLocodes = Collections.unmodifiableList(viaLocodes);
        this.transitDays = transitDays;
        this.estimatedPrice = estimatedPrice;
        this.voyageNumber = voyageNumber;
    }

    public List<String> viaLocodes() { return viaLocodes; }
    public int transitDays() { return transitDays; }
    public BigDecimal estimatedPrice() { return estimatedPrice; }
    public String voyageNumber() { return voyageNumber; }

    /**
     * 基準日から所要日数後の到着予定日が希望着日以内かどうかを返す。
     *
     * @param baseDate            基準日（通常は見積作成日 = 今日）
     * @param requestedArrivalDate 希望着日
     * @return 間に合う場合 {@code true}
     */
    public boolean isOnTime(LocalDate baseDate, LocalDate requestedArrivalDate) {
        LocalDate estimatedArrival = baseDate.plusDays(transitDays);
        return !estimatedArrival.isAfter(requestedArrivalDate);
    }

    /**
     * 指定されたクロックから現在日を取得し、希望着日に間に合うかどうかを返す。
     *
     * @param clock               現在日取得に使用するクロック（テスト時は {@link Clock#fixed} を渡す）
     * @param requestedArrivalDate 希望着日
     * @return 間に合う場合 {@code true}
     */
    public boolean isOnTime(Clock clock, LocalDate requestedArrivalDate) {
        return isOnTime(LocalDate.now(clock), requestedArrivalDate);
    }

    /**
     * 今日を基準日として希望着日に間に合うかどうかを返す。
     *
     * @param requestedArrivalDate 希望着日
     * @return 間に合う場合 {@code true}
     */
    public boolean isOnTime(LocalDate requestedArrivalDate) {
        return isOnTime(LocalDate.now(), requestedArrivalDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteOption that)) return false;
        return transitDays == that.transitDays
                && Objects.equals(viaLocodes, that.viaLocodes)
                && estimatedPrice.compareTo(that.estimatedPrice) == 0
                && Objects.equals(voyageNumber, that.voyageNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viaLocodes, transitDays, estimatedPrice.stripTrailingZeros(), voyageNumber);
    }
}
