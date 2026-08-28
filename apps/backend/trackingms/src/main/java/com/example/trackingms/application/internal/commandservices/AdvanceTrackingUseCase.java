package com.example.trackingms.application.internal.commandservices;

import org.springframework.stereotype.Service;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingNotifier;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 荷役の記録に応じて追跡を進める（US15-4・[ADR-023] 決定 5）。
 *
 * <p><strong>例外にしないことは、記録しないことではない。</strong>知らない種別も、進まない
 * 種別も「書き込まない」に落ちる。そこだけを見ていると、相手が新しい種別を送り始めたことに
 * 誰も気づかない。契約の食い違いは警告として残す——ただしデッドレターへは回さない
 * （回すと種別 1 つで後続の荷役まで止まる）。
 *
 * <p><strong>知らない追跡番号では止まらない。</strong>荷役の記録は実際に起きた作業であり、
 * こちらに追跡が無いことはある（発行のイベントを取りこぼした場合など）。ここで例外にすると
 * イベントがデッドレターへ回り、<strong>原因が直るまで後続の荷役も進まなくなる</strong>。
 * 取りこぼしは運用の照会（`dev:k8s:events:missing`）が拾う。
 */
@Service
public class AdvanceTrackingUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvanceTrackingUseCase.class);

    private final TrackingActivityRepository activities;
    private final LocationRepository locations;
    private final TrackingNotifier notifier;

    public AdvanceTrackingUseCase(TrackingActivityRepository activities,
            LocationRepository locations, TrackingNotifier notifier) {
        this.activities = activities;
        this.locations = locations;
        this.notifier = notifier;
    }

    /**
     * 追跡を進める。
     *
     * @param trackingNumber 追跡番号
     * @param handlingType 荷役の種別の名前
     * @param locationUnLocode 作業場所
     * @param completionTime 作業日時。<strong>経過にはこれを残す</strong>——受け取った時刻
     *     ではなく、実際に作業した時刻が荷主の読むものである
     */
    @org.springframework.transaction.annotation.Transactional
    public void advance(String trackingNumber, String handlingType, String locationUnLocode,
            java.time.Instant completionTime) {
        if (!TrackingStatus.isKnownHandlingType(handlingType)) {
            log.warn("知らない荷役の種別を受け取りました。追跡は進めません。"
                    + " trackingNumber={} handlingType={}", trackingNumber, handlingType);
            return;
        }
        activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))
                .ifPresent(current -> saveIfChanged(current,
                        current.afterHandling(handlingType, locationUnLocode), completionTime));
    }

    /**
     * 進んだときだけ書き込む。
     *
     * <p><strong>同じ内容の更新で行を触らない。</strong>集約は進まないとき同じものを返すので、
     * それを見て判断する。無条件に書くと、再配送のたびに {@code updated_at} が動き、
     * 「いつ状態が変わったか」が読めなくなる。
     */
    private void saveIfChanged(TrackingActivity current, TrackingActivity advanced,
            java.time.Instant completionTime) {
        if (advanced == current) {
            return;
        }
        activities.updateStatus(advanced);
        // **状態が動いたら、経過にも残す**（US18-3）。同じトランザクションで書く
        // ——別々になると、状態は動いたのに経過に出ない行ができ、荷主は「いつ変わったか」を
        // 読めない
        activities.appendEvent(advanced.trackingNumber(), new TrackingEvent(
                advanced.trackingStatus(), locationOf(advanced), completionTime,
                TrackingEvent.EventSource.HANDLING));
        notifier.statusChanged(advanced);
    }

    /**
     * 地点の名前はこちらのマスタから引く（[ADR-014]）。
     *
     * <p>イベントが運ぶのは UN/LOCODE だけである。集約はコードをそのまま名前として
     * 置いているので、ここで引き直す——引けなければ集約が置いたものをそのまま使う
     * （<strong>名前が引けないことで記録を止めない</strong>）。
     */
    private Location locationOf(TrackingActivity advanced) {
        return locations.findByUnLocode(advanced.currentLocation().unLocode())
                .orElseGet(advanced::currentLocation);
    }
}
