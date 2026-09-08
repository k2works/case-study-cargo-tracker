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
    MANUAL("MANUAL"),
    /**
     * 例外の起票・解決から動いた（US19・IT10）。
     *
     * <p>履歴の種別は起票なら {@code EXCEPTION}、解決なら {@code RESOLVED}
     * （data-model.md:572）。<b>どちらも「例外由来」だが、履歴では区別する</b>
     * ——起票と解決は逆向きの出来事で、同じ印にすると読めない。</p>
     */
    EXCEPTION("EXCEPTION"),
    /** 例外が解決して戻った（US19・IT10）。 */
    RESOLVED("RESOLVED");

    private final String eventType;

    StatusUpdateSource(String eventType) {
        this.eventType = eventType;
    }

    /** 履歴テーブルに書く種別（{@code tracking_event.event_type}）。 */
    public String eventType() {
        return eventType;
    }
}
