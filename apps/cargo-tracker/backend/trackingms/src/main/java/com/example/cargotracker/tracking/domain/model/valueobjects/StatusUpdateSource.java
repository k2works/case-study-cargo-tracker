package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 輸送状態が変わったきっかけ（data-model.md「tracking_event」の `event_type`）。
 *
 * <p><b>同じ {@code TransportStatusUpdatedEvent} を荷役由来と手動由来で共有する</b>
 * ため、どちらから来たかをイベントが持つ。持たせないと、履歴（{@code tracking_event}）が
 * 「誰の判断で動いたか」を出せない——荷役の記録なら作業実績、手動なら人の判断で、
 * 業務上の意味が違う。</p>
 */
public enum StatusUpdateSource {
    /** 荷役の記録から導いた（US15・IT9）。 */
    HANDLING("HANDLING"),
    /** 追跡管理者が手で更新した（US17）。 */
    MANUAL("MANUAL");

    private final String eventType;

    StatusUpdateSource(String eventType) {
        this.eventType = eventType;
    }

    /** 履歴テーブルに書く種別（{@code tracking_event.event_type}）。 */
    public String eventType() {
        return eventType;
    }
}
