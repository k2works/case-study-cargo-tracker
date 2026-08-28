package com.example.trackingms.application.internal.commandservices;

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
 * 通関の留置を、例外「税関保留」として自動で起票する（US29-5）。
 *
 * <p><strong>留め置かれたことが誰の目にも入らないと、貨物はそのまま止まる。</strong>
 * 追跡管理者の未解決一覧に現れて初めて、税関への問い合わせが始まる。
 *
 * <p><strong>手で起票する入口は使わない</strong>（[ADR-024] 決定 11）。仕組みが検知する
 * 種別は {@code detectException} から入る——{@code raiseException} は「人が判断して
 * 起票する」入口であり、税関保留はそこに載っていない（IT9 返済枠 0.4）。
 *
 * <p><strong>留置以外では何もしない。</strong>通関済・不可・審査中は、追跡管理者が
 * 動く事象ではない。通関済は引取のガードが通る合図であり、例外ではない。
 */
@Service
public class DetectCustomsHoldUseCase {

    private static final Logger log = LoggerFactory.getLogger(DetectCustomsHoldUseCase.class);

    /** 留置を表す通関状態。**送り手の語彙をそのまま使う**（契約が固定している）。 */
    private static final String HELD = "HELD";

    private final TrackingActivityRepository activities;
    private final TrackingNotifier notifier;

    public DetectCustomsHoldUseCase(TrackingActivityRepository activities,
            TrackingNotifier notifier) {
        this.activities = activities;
        this.notifier = notifier;
    }

    /**
     * 通関状態の変化を受けて起票する。
     *
     * <p><strong>未解決の例外があるときは起票しない。</strong>2 件目を許すと、発生前の
     * 状態が上書きされて解決しても戻れない（[ADR-024] 決定 2）。すでに何かが起きている
     * 貨物は、追跡管理者の一覧にもう載っている。
     *
     * <p><strong>知らない追跡番号では止まらない。</strong>例外にすると、後続のイベントも
     * 処理されなくなる。
     */
    @Transactional
    public void onCustomsStatusChanged(String trackingNumber, String toStatus, String reason,
            Instant changedAt) {
        if (!HELD.equals(toStatus)) {
            return;
        }
        activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))
                .ifPresentOrElse(
                        activity -> detect(activity, reason, changedAt),
                        () -> log.info("通関のイベントに一致する追跡がありません: trackingNumber={}",
                                trackingNumber));
    }

    private void detect(TrackingActivity activity, String reason, Instant changedAt) {
        if (activity.activeException().isPresent()) {
            // すでに一覧に載っている。2 件目を起票すると、解決したときに戻る先が失われる
            return;
        }
        TrackingActivity raised = activity.detectException(ExceptionType.CUSTOMS_HOLD,
                description(reason), changedAt);

        activities.updateStatus(raised);
        activities.saveException(raised.trackingNumber(), raised);
        activities.appendEvent(raised.trackingNumber(), new TrackingEvent(
                raised.trackingStatus(), raised.currentLocation(), changedAt,
                TrackingEvent.EventSource.EXCEPTION));
        notifier.exceptionRaised(raised);
    }

    /**
     * 発生状況。
     *
     * <p><strong>理由をそのまま載せる。</strong>この文言を読むのは追跡管理者であり、
     * 税関に問い合わせるときの手がかりになる（荷主のお知らせには種別も詳細も出さない
     * ——[ADR-024] 決定 3 の周辺で決めた形を維持する）。
     */
    private static String description(String reason) {
        return reason == null || reason.isBlank()
                ? "税関で留置されています"
                : "税関で留置されています: " + reason;
    }
}
