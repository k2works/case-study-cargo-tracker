package com.example.trackingms.infrastructure.persistence;

import com.example.trackingms.application.port.TrackingLookupLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 公開照会の記録（[ADR-024] 決定 7）。
 *
 * <p><strong>記録に失敗しても照会は返す。</strong>記録のために荷主の照会を止めない。
 *
 * <p><strong>ただし失敗を黙って捨てない。</strong>「例外にしない」は「記録しない」では
 * ない——書けなかったことが誰にも見えないと、総当たりの検知が働いていないことにも
 * 気づけない。
 */
public class MyBatisTrackingLookupLogger implements TrackingLookupLogger {

    private static final Logger log = LoggerFactory.getLogger(MyBatisTrackingLookupLogger.class);

    /** 名乗りは長いことがある。列に収まる長さで切る。 */
    private static final int USER_AGENT_LIMIT = 255;

    /** 番号は読めない値も残す。総当たりの手がかりになるため、長さだけを制限する。 */
    private static final int TRACKING_NUMBER_LIMIT = 40;

    private final TrackingLookupLogMapper mapper;

    public MyBatisTrackingLookupLogger(TrackingLookupLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void log(String trackingNumber, String clientIp, String userAgent, boolean found) {
        try {
            mapper.insert(truncate(trackingNumber, TRACKING_NUMBER_LIMIT), clientIp,
                    truncate(userAgent, USER_AGENT_LIMIT), found);
        } catch (RuntimeException e) {
            log.warn("追跡照会の記録に失敗しました。照会そのものは返します。"
                    + " clientIp={} found={}", clientIp, found, e);
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
