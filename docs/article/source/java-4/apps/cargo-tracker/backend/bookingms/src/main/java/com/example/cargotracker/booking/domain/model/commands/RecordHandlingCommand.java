package com.example.cargotracker.booking.domain.model.commands;

import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷役が記録されたことを予約に反映する（US15・US28 / 不変条件 12）。
 *
 * <p><b>`BookingReactionHandler` が送る。</b> 荷役は handlingms で起き、予約は
 * その事実を「最後の荷役」として一覧に出す。営業と経路設計者が、貨物がいまどこまで
 * 進んだかを予約の画面から読めるようにする。</p>
 *
 * <p><b>最初の受領で予約が輸送中になる。</b> 状態遷移図（domain-model.md）の
 * {@code TRACKING_ISSUED → IN_TRANSIT}。</p>
 *
 * <p><b>予定外なら経路設計の状態を誤配にする</b>（不変条件 12）。経路設計者が
 * 現在地起点で組み直すまで、その予約は作業一覧に残る。</p>
 */
public record RecordHandlingCommand(
        @TargetEntityId String bookingId,
        String activityId,
        String handlingType,
        String unLocode,
        boolean offRoute,
        Instant completedAt) {
}
