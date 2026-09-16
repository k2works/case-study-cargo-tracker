package com.example.cargotracker.routing.domain.model.events;

import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 航海スケジュールを更新した（UC19 / US25）。
 *
 * <p>契約イベントではない（routingms の内側だけで読む）。{@code VoyageRegisteredEvent}
 * と同じ形にしているのは、投影が「登録」と「更新」で同じ列を書くためである。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「登録されていない航海は更新できない」も「キャンセル済みは更新できない」も
 * 素通りする。それでもテストは緑になる（IT2 で実測）。</p>
 */
public record VoyageScheduleUpdatedEvent(
        @EventTag(key = "voyageNumber") String voyageNumber,
        String carrierCode,
        String carrierName,
        String vesselName,
        List<Movement> movements,
        List<String> acceptedCargoTypes,
        String updatedBy,
        // 直した時刻。投影が現在時刻で決めない。決めると、投影を読み直すたびに
        // 「いつ直したか」が動き、読み直した日時が最終更新として画面に出る。
        java.time.Instant updatedAt) {

    /** 航海内の港間移動。順序は並び順そのもの。 */
    public record Movement(
            String departureUnLocode,
            String arrivalUnLocode,
            Instant departureAt,
            Instant arrivalAt) {
    }
}
