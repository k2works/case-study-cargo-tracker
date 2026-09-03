package com.example.cargotracker.booking.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 貨物予約の入出力（UI 設計 S21）。 */
public final class BookingDtos {

    private BookingDtos() {
    }

    /**
     * 予約の登録。
     *
     * <p>到着期限は日付で受ける。時刻付きにすると、当日着を「間に合わない」と
     * 判定する経路ができる（不変条件 5）。</p>
     *
     * <p>種別ごとの必須項目（危険物申告・温度条件）はここでは検査しない。判断は
     * 集約が持ち、入口は形だけを見る。両方に置くと、集約を直したときに入口だけが
     * 古い規則で弾く。</p>
     */
    public record BookCargoRequest(
            @NotBlank String shipperId,
            @NotBlank String originUnLocode,
            @NotBlank String destinationUnLocode,
            @NotNull LocalDate arrivalDeadline,
            @NotBlank String cargoType,
            @NotNull BigDecimal weightKg,
            @NotNull BigDecimal lengthCm,
            @NotNull BigDecimal widthCm,
            @NotNull BigDecimal heightCm,
            @NotNull Integer quantity,
            @NotBlank String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC) {
    }

    public record BookCargoResponse(String bookingId) {
    }
}
