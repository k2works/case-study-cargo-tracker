package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.CreateEstimateCommand;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積の依頼（受入基準 01-1）。
 *
 * @param originUnLocode 出発地
 * @param destinationUnLocode 目的地
 * @param arrivalDeadline 希望期限。<strong>日付で渡す</strong>（[ADR-017] 決定 3 と同じ）
 * @param cargoType 貨物種別
 * @param weightKg 重量
 */
public record CreateEstimateRequest(String originUnLocode, String destinationUnLocode,
        LocalDate arrivalDeadline, String cargoType, BigDecimal weightKg) {

    CreateEstimateCommand toCommand() {
        return new CreateEstimateCommand(originUnLocode, destinationUnLocode, arrivalDeadline,
                cargoType, weightKg);
    }
}
