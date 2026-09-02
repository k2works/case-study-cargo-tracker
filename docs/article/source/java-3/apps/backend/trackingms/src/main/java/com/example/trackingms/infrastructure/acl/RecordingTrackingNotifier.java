package com.example.trackingms.infrastructure.acl;

import com.example.trackingms.domain.repository.TrackingNoticeRepository;
import com.example.trackingms.application.internal.outboundservices.acl.TrackingNotifier;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingNotice;
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
        noteForShipper(activity, "お荷物の状況が「%s」になりました。"
                .formatted(activity.trackingStatus().label()));
    }

    /**
     * <strong>種別を書かない。</strong>
     *
     * <p>この文言は認証の外にある画面へ出る（[ADR-024] 決定 5 で例外の詳細は返さないと
     * 決めた）。上の欄で「問題が起きています」としか書かないのに、お知らせで「紛失」と
     * 書けば<strong>隠した意味が無い</strong>。
     *
     * <p>とくに「紛失」は補償の話に直結する言葉である。荷受人が荷主から何も聞いていない
     * 段階でこれを読むと、その日のうちにクレームになる——現場の慣行では、紛失は担当者から
     * 口頭で伝えるのが先である。
     */
    @Override
    public void exceptionRaised(TrackingActivity activity) {
        noteForShipper(activity, "お荷物に問題が発生しました。詳しくはご依頼元へお問い合わせください。");
    }

    /**
     * <strong>荷主が知りたいのは「で、いつ着くのか」である。</strong>
     *
     * <p>状態だけを伝えても、遅れが解消したのかは分からない。新しい到着予定日が
     * 決まっていれば、それを書く（US19-4）。
     */
    @Override
    public void exceptionResolved(TrackingActivity activity) {
        String arrival = activity.estimatedArrival()
                .map("新しい到着予定日は %s です。"::formatted)
                .orElse("");
        noteForShipper(activity, "お荷物の問題は解決しました。状況は「%s」です。%s"
                .formatted(activity.trackingStatus().label(), arrival));
    }

    private void noteForShipper(TrackingActivity activity, String message) {
        notices.save(activity.trackingNumber(), new TrackingNotice(clock.instant(), message));
    }
}
