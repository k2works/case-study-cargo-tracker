package com.example.cargotracker.handling.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 通関完了を荷主・荷受人へ知らせた（handlingms の内部イベント / US29 §受入基準 4）。
 *
 * <p><b>送信基盤はスコープ外</b>（ui_design.md）。通知は電話・メールの手作業で、
 * システムは「いつ・何を伝えたか」の記録だけを残す。</p>
 *
 * <p><b>記録と読み口は対で出す。</b> 記録だけを積んで読み口を出さないと、
 * 受入基準の満たし方そのものが成り立たない（IT10 で 2 回踏んだ）。読み口は
 * 申告詳細（S53）の履歴である。</p>
 */
public record CustomsClearanceNotifiedEvent(
        @EventTag(key = "declarationNumber") String declarationNumber,
        String trackingNumber,
        // 何を伝えたか。手作業で伝える人がそのまま読み上げられる文にする。
        String content,
        Instant notifiedAt) {
}
