package com.example.trackingms.infrastructure.notification;

import com.example.trackingms.application.port.TrackingNoticeRepository;
import com.example.trackingms.application.port.TrackingNotifier;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingNotice;
import java.time.Clock;

/**
 * 荷主への通知を<strong>記録で代替する</strong>（[ADR-024] 決定 9）。
 *
 * <p><strong>メールを送らない。</strong>送る仕組みがまだ無いためであり、送ったことにも
 * しない。通知したという事実を残し、荷主は追跡照会の画面で読む。
 *
 * <p><strong>ここに送信のコードを足さない。</strong>メール送信を実装する日は、この実装を
 * 差し替える——業務のコード（{@code ManageTrackingUseCase}）は動かない。それがポートを
 * 挟んだ理由である。
 *
 * <p>文言に<strong>社内の手がかりを書かない</strong>。この文言は認証の外にある画面へ出る。
 */
public class RecordingTrackingNotifier implements TrackingNotifier {

    private final TrackingNoticeRepository notices;
    private final Clock clock;

    public RecordingTrackingNotifier(TrackingNoticeRepository notices, Clock clock) {
        this.notices = notices;
        this.clock = clock;
    }

    @Override
    public void statusChanged(TrackingActivity activity) {
        record(activity, "お荷物の状況が「%s」になりました。"
                .formatted(activity.trackingStatus().label()));
    }

    @Override
    public void exceptionRaised(TrackingActivity activity) {
        String kind = activity.activeException()
                .map(exception -> exception.exceptionType().label())
                .orElse("問題");
        record(activity, "お荷物に%sが発生しました。詳しくはご依頼元へお問い合わせください。"
                .formatted(kind));
    }

    @Override
    public void exceptionResolved(TrackingActivity activity) {
        record(activity, "お荷物の問題は解決しました。状況は「%s」です。"
                .formatted(activity.trackingStatus().label()));
    }

    private void record(TrackingActivity activity, String message) {
        notices.save(activity.trackingNumber(), new TrackingNotice(clock.instant(), message));
    }
}
