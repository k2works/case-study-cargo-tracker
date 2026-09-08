package com.example.cargotracker.tracking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 例外への対応を始める（UC16 / US19 §受入基準 4）。
 *
 * @param newEstimatedArrival 新しい到着予定日（{@code YYYY-MM-DD}）。<b>日付だけ</b>
 *     ——遅延の連絡で時刻まで約束できることは少ない
 * @param plan 対応方針。荷主へ伝える内容そのもの
 */
public record StartExceptionResponseCommand(
        @TargetEntityId String trackingNumber,
        String exceptionId,
        String newEstimatedArrival,
        String plan,
        String respondedBy) {
}
