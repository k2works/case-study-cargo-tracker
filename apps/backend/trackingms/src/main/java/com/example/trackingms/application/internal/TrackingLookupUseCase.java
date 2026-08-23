package com.example.trackingms.application.internal;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingLookupLogger;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.List;
import java.util.Optional;

/**
 * 追跡を照会する（US18・[ADR-024] 決定 5・7）。
 *
 * <p><strong>認証の外にある唯一の業務経路である。</strong>照会は成否に関わらず記録する
 * ——見つからなかった照会こそ、総当たりを見つける材料である（決定 7）。
 */
public class TrackingLookupUseCase {

    /**
     * 返す経過の上限。
     *
     * <p>上限が無いと、件数が増えた日に照会が開かなくなる。1 つの貨物の経過が 200 を
     * 超えることは実務では無い。
     */
    public static final int HISTORY_LIMIT = 200;

    private final TrackingActivityRepository activities;
    private final TrackingLookupLogger lookupLogger;

    public TrackingLookupUseCase(TrackingActivityRepository activities,
            TrackingLookupLogger lookupLogger) {
        this.activities = activities;
        this.lookupLogger = lookupLogger;
    }

    /**
     * 追跡番号で照会する。
     *
     * <p><strong>形式が違っても「見つかりません」で返す。</strong>形式の誤りを別の答えに
     * すると、番号の形を総当たりの手がかりとして教えることになる。
     *
     * @param clientIp 呼び出し元。認証が無いので、これが「誰が」にあたる
     * @return 見つからなければ空
     */
    public Optional<TrackingActivity> lookUp(String trackingNumber, String clientIp,
            String userAgent) {
        Optional<TrackingActivity> found = restore(trackingNumber)
                .flatMap(activities::findByTrackingNumber);
        // **記録は照会の成否に関わらず残す**（[ADR-024] 決定 7）
        lookupLogger.log(trackingNumber, clientIp, userAgent, found.isPresent());
        return found;
    }

    /** 1 つの貨物の経過（US18-3）。荷役の記録と手動更新の両方が並ぶ。 */
    public List<TrackingEvent> events(TrackingActivity activity) {
        return activities.findEvents(activity.trackingNumber(), HISTORY_LIMIT);
    }

    /**
     * 番号として読めるものだけを引く。
     *
     * <p><strong>解析だけを囲む。</strong>読み出しまで囲むと、復元の例外が
     * 「見つかりません」に化けて原因が残らない。
     */
    private static Optional<TrackingNumber> restore(String trackingNumber) {
        try {
            return Optional.of(TrackingNumber.of(trackingNumber));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
