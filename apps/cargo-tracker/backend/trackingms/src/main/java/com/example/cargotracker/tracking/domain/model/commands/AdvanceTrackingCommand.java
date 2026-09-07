package com.example.cargotracker.tracking.domain.model.commands;

import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷役の記録から貨物状態を進める（UC14 / US15）。
 *
 * <p><b>手動更新（{@code UpdateTransportStatusCommand}・US17）と同じ集約に入る。</b>
 * 名前で区別が付くようにする——あちらは<b>人が状態を名指しする</b>操作で、
 * こちらは<b>荷役が起きたので進める</b>。進めた先は種別と港が決める
 * （{@code TransportStatus#afterHandling}）。</p>
 *
 * <p><b>handlingms の型を持ち込まない。</b> 種別は名前で受ける（契約は文字列で運ぶ）。</p>
 */
public record AdvanceTrackingCommand(
        @TargetEntityId String trackingNumber,
        String handlingType,
        String unLocode,
        boolean finalPort,
        boolean offRoute,
        String operator,
        Instant completedAt) {
}
