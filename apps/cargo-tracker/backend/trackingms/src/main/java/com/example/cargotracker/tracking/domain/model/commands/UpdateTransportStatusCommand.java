package com.example.cargotracker.tracking.domain.model.commands;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 追跡管理者が輸送状態を手で更新する（UC14 / US17 §受入基準 2）。
 *
 * <p><b>荷役由来の {@code AdvanceTrackingCommand}（IT9）と同じ集約に入る。</b>
 * 名前で区別が付くようにする——あちらは「荷役が起きたので進める」で、進めた先は
 * 荷役種別が決める。こちらは<b>人が状態を名指しする</b>。出港のように荷役として
 * 記録されない動きは、手で入れるほかない（{@code IN_TRANSIT}）。</p>
 *
 * <p><b>trackingms の内部コマンド</b>（契約に載せない）。{@link TransportStatus} は
 * trackingms の型で、契約に載せると片方が値を足すだけでもう一方が復元できなくなる。</p>
 *
 * @param location 状態が変わった場所（UN/LOCODE）。荷主の照会で「いまどこか」に出る
 * @param occurredAt 状態が変わった日時。<b>記録した日時ではない</b>——後から入力すると
 *     ずれる。並び順は起きた時刻で決める
 */
public record UpdateTransportStatusCommand(
        @TargetEntityId String trackingNumber,
        TransportStatus newStatus,
        String location,
        Instant occurredAt,
        String updatedBy) {
}
