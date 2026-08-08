package com.example.cargotracker.handling.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;

/**
 * 荷役作業の登録コマンド（US15）。
 *
 * <p>画面が受け取るのは追跡番号だが、<strong>コマンドは予約 ID を持つ</strong>。
 * 追跡番号から予約を引き当てるのはアプリケーション層の仕事であり、
 * 集約が知る必要があるのは「どの予約に対する作業か」だけである。
 *
 * @param cargoBookingId 予約 ID
 * @param type           荷役種別
 * @param completionTime 作業日時
 * @param location       作業場所
 * @param voyageNumber   航海番号（積込・荷降しでは必須）
 * @param operatorName   作業員名（任意）
 */
public record RegisterHandlingCommand(
        CargoBookingId cargoBookingId,
        HandlingType type,
        Instant completionTime,
        Location location,
        HandlingVoyageNumber voyageNumber,
        String operatorName) {
}
