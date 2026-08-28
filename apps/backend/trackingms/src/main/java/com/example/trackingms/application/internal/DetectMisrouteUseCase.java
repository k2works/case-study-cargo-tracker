package com.example.trackingms.application.internal;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingNotifier;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.Instant;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予定ルート外の荷役を、例外「誤配」として自動で起票する（US28-2）。
 *
 * <p><strong>誤配は発見が遅れるほど被害が膨らむ。</strong>貨物は目的地から遠ざかり続け、
 * 納期遅延と輸送コストが積み上がる。追跡管理者の未解決一覧に現れて初めて、
 * 経路の組み直しが始まる。
 *
 * <p><strong>判定はしない</strong>（[ADR-026] 決定 1）。{@code offRoute} は handlingms が
 * 旅程と作業場所を照合した結果である（[ADR-023] 決定 3）——ここで判定し直すと、
 * 旅程の写しをもう 1 つ持つことになり、片方だけが古い旅程で判定する状態が生まれる。
 *
 * <p><strong>手で起票する入口は使わない</strong>（[ADR-024] 決定 11）。仕組みが検知する
 * 種別は {@code detectException} から入る——{@code raiseException} は「人が判断して
 * 起票する」入口であり、誤配はそこに載っていない。
 *
 * <p>IT9 の {@code DetectCustomsHoldUseCase} と同じ道である。<strong>違うのは起点だけ</strong>
 * ——通関は「状態が変わったとき」、誤配は<strong>荷役の記録そのもの</strong>である
 * （あとから誰かが判断するものではない）。
 */
@Service
public class DetectMisrouteUseCase {

    private static final Logger log = LoggerFactory.getLogger(DetectMisrouteUseCase.class);

    private final TrackingActivityRepository activities;
    private final TrackingNotifier notifier;

    public DetectMisrouteUseCase(TrackingActivityRepository activities,
            TrackingNotifier notifier) {
        this.activities = activities;
        this.notifier = notifier;
    }

    /**
     * 荷役の記録を受けて起票する。
     *
     * <p><strong>予定どおりの荷役では何もしない。</strong>
     *
     * <p><strong>未解決の例外があるときは起票しない。</strong>2 件目を許すと、発生前の
     * 状態が上書きされて解決しても戻れない（[ADR-024] 決定 2）。
     *
     * <p><strong>知らない追跡番号では止まらない。</strong>例外にすると、後続のイベントも
     * 処理されなくなる。
     */
    @Transactional
    public void onHandlingActivityRegistered(String trackingNumber, String locationUnLocode,
            Instant completionTime, boolean offRoute) {
        if (!offRoute) {
            return;
        }
        activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))
                .ifPresentOrElse(
                        activity -> detect(activity, locationUnLocode, completionTime),
                        () -> log.info("荷役のイベントに一致する追跡がありません: trackingNumber={}",
                                trackingNumber));
    }

    private void detect(TrackingActivity activity, String locationUnLocode, Instant at) {
        if (activity.activeException().isPresent()) {
            // すでに一覧に載っている。2 件目を起票すると、解決したときに戻る先が失われる
            return;
        }
        TrackingActivity raised = activity.detectException(ExceptionType.MISROUTE,
                description(locationUnLocode), at);

        activities.updateStatus(raised);
        activities.saveException(raised.trackingNumber(), raised);
        activities.appendEvent(raised.trackingNumber(), new TrackingEvent(
                raised.trackingStatus(), raised.currentLocation(), at,
                TrackingEvent.EventSource.EXCEPTION));
        notifier.exceptionRaised(raised);
    }

    /**
     * 発生状況。
     *
     * <p><strong>どこで外れたかを載せる。</strong>この文言を読むのは追跡管理者であり、
     * 経路設計者へ渡すときの手がかりになる。「誤配が起きました」だけでは、
     * 受け取った人は場所を別に探すことになる。
     */
    private static String description(String locationUnLocode) {
        return locationUnLocode == null || locationUnLocode.isBlank()
                ? "予定ルート外の場所で荷役が行われました"
                : "予定ルート外の場所（%s）で荷役が行われました".formatted(locationUnLocode);
    }
}
