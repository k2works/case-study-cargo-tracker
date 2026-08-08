package com.example.cargotracker.shared.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 結果整合の取りこぼしを記録する（ADR-009 の代償への手当て）。
 *
 * <p>BC 間の状態伝播をドメインイベントに変えた結果、<strong>購読側の失敗を
 * 利用者の画面に返せなくなった</strong>。同期で呼んでいたときは「他の操作が先に
 * 行われました」と出せていたものが、いまは購読側の中で終わる。
 *
 * <p><strong>これは結果整合を選んだ以上避けられない代償である。</strong>
 * 問題は代償そのものではなく、<strong>気づく手段を用意しないこと</strong>である。
 * ログに出すだけでは「誰も見ない場所に置いた」のと同じであり、
 * 件数として数えられて初めて閾値を決めて気づける。
 *
 * <p>件数は気づくため、ログは直すためにある。両方を 1 か所で出す。
 *
 * <p>運用の手順は {@code docs/design/operation.md} を参照。
 */
@Component
public class EventualConsistencySkips {

    /** メトリクス名。運用手順書が参照するため定数で公開する。 */
    public static final String METRIC_NAME = "cargotracker.eventual.consistency.skips";

    private static final Logger LOG =
            LoggerFactory.getLogger(EventualConsistencySkips.class);

    private final MeterRegistry registry;

    public EventualConsistencySkips(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 反映できなかったことを記録する。
     *
     * <p><strong>対象の識別子をタグにしない。</strong> 予約 ID や追跡番号は値の種類が
     * 際限なく増え、時系列データベースを膨張させる。<strong>数えるのは購読者と理由まで</strong>
     * とし、どのレコードだったかはログで追う。
     *
     * @param subscriber 購読側の BC 名（{@code booking} / {@code tracking}）
     * @param reason     反映できなかった理由（{@code NOT_FOUND} / {@code CONFLICTED}）
     * @param key        対象の識別子（予約 ID・追跡番号）。ログにのみ出す
     */
    public void recordSkip(String subscriber, String reason, String key) {
        registry.counter(METRIC_NAME, "subscriber", subscriber, "reason", reason)
                .increment();
        LOG.warn("結果整合の反映を取りこぼした subscriber={} reason={} key={}",
                subscriber, reason, key);
    }
}
