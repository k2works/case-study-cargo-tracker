package com.example.trackingms.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 公開照会の記録（[ADR-024] 決定 7）。
 *
 * <p><strong>記録に失敗しても照会は返す。ただし失敗を黙って捨てない。</strong>
 * 「例外にしない」は「記録しない」ではない——書けなかったことが誰にも見えないと、
 * 総当たりの検知が働いていないことにも気づけない。
 */
@DisplayName("公開照会の記録")
class MyBatisTrackingLookupLoggerTest {

    private final List<String> inserted = new ArrayList<>();

    private TrackingLookupLogMapper mapperThat(Runnable behaviour) {
        return new TrackingLookupLogMapper() {
            @Override
            public void insert(String trackingNumber, String clientIp, String userAgent,
                    boolean found) {
                behaviour.run();
                inserted.add("%s|%s|%s|%s".formatted(trackingNumber, clientIp, userAgent, found));
            }

            @Override
            public int countSince(String clientIp, Instant since) {
                return inserted.size();
            }
        };
    }

    @Test
    @DisplayName("照会の中身を、そのまま残す")
    void recordsTheLookup() {
        new MyBatisTrackingLookupLogger(mapperThat(() -> { }))
                .log("TRK-20260823-0001", "203.0.113.10", "curl/8", false);

        assertThat(inserted).containsExactly("TRK-20260823-0001|203.0.113.10|curl/8|false");
    }

    /**
     * <strong>記録に失敗しても照会は返す。</strong>
     *
     * <p>記録のために荷主の照会を止めない。
     */
    @Test
    @DisplayName("記録に失敗しても、照会そのものは止めない")
    void doesNotFailTheLookupWhenTheLogFails() {
        assertThatCode(() -> new MyBatisTrackingLookupLogger(
                mapperThat(() -> {
                    throw new IllegalStateException("DB へ書けません");
                })).log("TRK-20260823-0001", "203.0.113.10", "curl/8", true))
                .doesNotThrowAnyException();
    }

    /**
     * <strong>失敗を黙って捨てない。</strong>
     *
     * <p>書けなかったことが誰にも見えないと、総当たりの検知が働いていないことにも
     * 気づけない。
     */
    @Test
    @DisplayName("記録に失敗したことは、警告として残る")
    void warnsWhenTheLogFails() {
        Logger logger = (Logger) LoggerFactory.getLogger(MyBatisTrackingLookupLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        new MyBatisTrackingLookupLogger(mapperThat(() -> {
            throw new IllegalStateException("DB へ書けません");
        })).log("TRK-20260823-0001", "203.0.113.10", "curl/8", true);

        assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList())
                .as("記録に失敗したことが、どこにも残っていない")
                .isNotEmpty();
    }

    /**
     * <strong>読めない値も残す。</strong>総当たりの手がかりになる。
     *
     * <p>ただし列に収まる長さで切る——切らないと、長い値 1 つで記録そのものが落ちる。
     */
    @Test
    @DisplayName("長すぎる値は切って残す")
    void truncatesOverlongValues() {
        new MyBatisTrackingLookupLogger(mapperThat(() -> { }))
                .log("X".repeat(100), "203.0.113.10", "U".repeat(500), false);

        assertThat(inserted.get(0).split("\\|")[0]).hasSize(40);
        assertThat(inserted.get(0).split("\\|")[2]).hasSize(255);
    }

    /** 名乗らない相手もいる。空で落ちない。 */
    @Test
    @DisplayName("名乗りが無くても記録する")
    void recordsWithoutAUserAgent() {
        new MyBatisTrackingLookupLogger(mapperThat(() -> { }))
                .log("TRK-20260823-0001", "203.0.113.10", null, false);

        assertThat(inserted).containsExactly("TRK-20260823-0001|203.0.113.10|null|false");
    }
}
