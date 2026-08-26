package com.example.bookingms.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積の依頼（受入基準 01-1）。
 *
 * <p>入力は 5 項目——出発地・目的地・希望期限・貨物種別・重量。
 *
 * @param originUnLocode 出発地
 * @param destinationUnLocode 目的地
 * @param arrivalDeadline 希望期限
 * @param cargoType 貨物種別
 * @param weightKg 重量
 */
public record CreateEstimateCommand(String originUnLocode, String destinationUnLocode,
        LocalDate arrivalDeadline, String cargoType, BigDecimal weightKg) {
}
