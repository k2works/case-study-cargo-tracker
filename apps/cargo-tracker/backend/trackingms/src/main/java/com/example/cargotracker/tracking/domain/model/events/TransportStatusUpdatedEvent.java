package com.example.cargotracker.tracking.domain.model.events;

import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 輸送状態が変わった（UC14 / US15・US17）。<b>trackingms の内部イベント</b>。
 *
 * <p><b>荷役由来と手動由来で同じイベントを使う。</b> 起きた事実は同じ「状態が変わった」で、
 * 違うのはきっかけだけ（{@link StatusUpdateSource}）。分けると投影が 2 つの形を
 * 読み分けることになり、履歴の並びを組むたびに両方を触る。</p>
 *
 * <p><b>{@code previousStatus} を載せる。</b> 履歴に「何から何へ」を出すため。投影は
 * コマンドを読まず、直前の行も引かない——引くと、再配送や順序の入れ替わりで壊れる
 * （イベントは購読側の投影が作れる分を運ぶ）。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「例外発生中は動かさない」「同じ状態へは動かさない」守りが丸ごと素通りする。</p>
 *
 * @param occurredAt 状態が変わった日時（業務上の時刻）
 * @param recordedAt 記録した日時（システムの時刻）。<b>別に持つ</b>——後から入力した
 *     記録を、起きた順と入れた順の両方で追えるようにする
 */
public record TransportStatusUpdatedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        TransportStatus previousStatus,
        TransportStatus newStatus,
        StatusUpdateSource source,
        String location,
        Instant occurredAt,
        String updatedBy,
        Instant recordedAt) {
}
