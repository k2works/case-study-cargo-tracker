package com.example.trackingms.application.internal;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingNoticeRepository;
import com.example.trackingms.domain.model.TrackingNotice;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.Clock;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * キャンセルが確定したことを、荷主のお知らせに残す（US30-6・[ADR-025] 決定 3）。
 *
 * <p><strong>状態は足さない。</strong>{@code TrackingStatus} に {@code CANCELLED} を
 * 足すと、進行の並び（{@code canAdvanceTo} の判定）に「進まない値」がもう 1 つ増える。
 * IT8 で {@code EXCEPTION} / {@code UNKNOWN} の 2 値が並び順の外にあることが実バグを
 * 生んだばかりである。<strong>お知らせ（IT8 で作った通知の代替の器）に記録する。</strong>
 *
 * <p><strong>知らない追跡番号では止まらない。</strong>例外にすると、後続のイベントも
 * 処理されなくなる。
 */
@Service
public class NoteCancellationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NoteCancellationUseCase.class);

    /**
     * 荷主に見せる文言。
     *
     * <p><strong>社内の手がかりを書かない。</strong>認証の外にある画面へ出るため、
     * 誰の判断で止まったか・どこで降ろすかは書かない。荷主が知りたいのは
     * 「自分の申し入れが通ったか」である。
     */
    static final String MESSAGE = "この輸送はキャンセルとなりました。詳しくはご依頼元へお問い合わせください。";

    private final TrackingActivityRepository activities;
    private final TrackingNoticeRepository notices;
    private final Clock clock;

    public NoteCancellationUseCase(TrackingActivityRepository activities,
            TrackingNoticeRepository notices, Clock clock) {
        this.activities = activities;
        this.notices = notices;
        this.clock = clock;
    }

    /**
     * お知らせに残す。
     *
     * <p><strong>冪等である。</strong>同じ内容のお知らせが 2 回届いても、2 通目は残さない
     * ——荷主の画面に同じ文が 2 行並ぶ。
     */
    @Transactional
    public void note(String trackingNumber) {
        TrackingNumber number = TrackingNumber.of(trackingNumber);
        activities.findByTrackingNumber(number).ifPresentOrElse(
                activity -> noteOnce(number),
                () -> log.info("キャンセルのイベントに一致する追跡がありません: trackingNumber={}",
                        trackingNumber));
    }

    private void noteOnce(TrackingNumber number) {
        boolean alreadyNoted = notices.findByTrackingNumber(number, NOTICE_LIMIT).stream()
                .anyMatch(notice -> MESSAGE.equals(notice.message()));
        if (alreadyNoted) {
            return;
        }
        notices.save(number, new TrackingNotice(clock.instant(), MESSAGE));
    }

    /** 重複を見るために読む件数。**荷主の画面に出る件数と同じでよい**。 */
    private static final int NOTICE_LIMIT = 20;
}
