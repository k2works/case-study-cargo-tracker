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
     * 貨物仕様と輸送条件の入力。受付（US04）と修正（US32）で同じ形にする。
     *
     * <p>別々に持つと、片方にだけ項目を足したときに「登録では入れられるのに
     * 修正では消える」が生まれる。</p>
     */
    public interface CargoFields {
        String originUnLocode();

        String destinationUnLocode();

        LocalDate arrivalDeadline();

        String cargoType();

        BigDecimal weightKg();

        BigDecimal lengthCm();

        BigDecimal widthCm();

        BigDecimal heightCm();

        Integer quantity();

        String productName();

        String hazardImoClass();

        String hazardUnNumber();

        BigDecimal temperatureMinC();

        BigDecimal temperatureMaxC();
    }

    /**
     * 修正の入力（US32）。
     *
     * <p>予約 ID は経路が持つ。荷主は変えられない（不変条件 1）。荷主を間違えたなら、
     * それは別の予約である。</p>
     */
    public record UpdateBookingRequest(
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
            BigDecimal temperatureMaxC) implements CargoFields {
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
            BigDecimal temperatureMaxC) implements CargoFields {
    }

    public record BookCargoResponse(String bookingId) {
    }
}
