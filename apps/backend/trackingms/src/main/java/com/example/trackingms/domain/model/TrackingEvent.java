package com.example.trackingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;

/**
 * 追跡の出来事（US17-3・US18-3）。
 *
 * <p><strong>荷役の記録と手動更新の両方が、同じ形で積まれる。</strong>別々に持つと、
 * 荷主に見せる 1 本の経過を 2 つの表から組み立てることになる。
 *
 * <p>残すのは<strong>「どうなったか」</strong>であって「何が起きたか」ではない
 * ——荷主が読むのは状態であり、荷役の種別ではない。
 *
 * @param trackingStatus 遷移した先の状態
 * @param location 発生した場所
 * @param occurredAt 業務上の発生時刻。記録した時刻ではない
 * @param source どこから来たか。運用の問い合わせで要る
 */
public record TrackingEvent(TrackingStatus trackingStatus, Location location, Instant occurredAt,
        EventSource source) {

    /** 出来事の出どころ。 */
    public enum EventSource {
        /** 荷役の記録から（US15）。 */
        HANDLING,
        /** 追跡管理者が手で入れた（US17）。 */
        MANUAL,
        /** 例外の起票・解決（US19・US20）。 */
        EXCEPTION
    }

    /** 検査はここに置く。名前のある入口にだけ置くと、正準コンストラクタから素通りできる。 */
    public TrackingEvent {
        if (trackingStatus == null || location == null || occurredAt == null || source == null) {
            throw new IllegalArgumentException("状態・場所・日時・出どころは必須です");
        }
    }
}
