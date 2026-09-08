package com.example.cargotracker.tracking.domain.model.events;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 輸送中の例外が起票された（UC16 / US19 §受入基準 1）。
 *
 * <p><b>投影が作れる分を運ぶ。</b> 投影はコマンドを読まない——一覧（S42）が要る
 * 項目を、ここに載っている値だけで作れなければならない。{@code urgent} は
 * {@code ExceptionType#urgent} の<b>結果</b>で、投影は判定を書き直さない。</p>
 *
 * <p>{@code statusBeforeException} を載せるのは、<b>戻る先を投影も読めるように</b>
 * するため。集約が覚えているが、画面が「解決すると何に戻るか」を出せない。</p>
 */
public record TrackingExceptionRegisteredEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String exceptionId,
        String exceptionType,
        Instant occurredAt,
        String unLocode,
        String description,
        boolean urgent,
        TransportStatus statusBeforeException,
        String reportedBy,
        Instant registeredAt) {
}
