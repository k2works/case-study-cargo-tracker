package com.example.cargotracker.tracking.interfaces.events;

import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.tracking.application.internal.commandservices
        .RaiseTrackingExceptionCommandService;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 誤配を例外として起票する（US28）。
 *
 * <p><strong>荷役の登録から起票する。</strong> 誤配は「予定ルートに無い作業が
 * 記録された」ことで成立する。追跡管理者が手で起票するものではない
 * （手で起票できると、荷役の記録が無いのに誤配の例外だけがある状態を作れる）。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、
 * 荷役の登録が巻き戻ったときに例外だけが残る。
 *
 * <p>誤配の判定そのものは Handling が行い、イベントは<strong>結果だけを運ぶ</strong>。
 * 予定ルートを Tracking が知る必要は無い（ADR-012）。
 */
@Component
public class TrackingMisrouteEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "tracking-misroute";

    private final RaiseTrackingExceptionCommandService exceptionService;
    private final EventualConsistencySkips skips;

    public TrackingMisrouteEventHandler(
            RaiseTrackingExceptionCommandService exceptionService,
            EventualConsistencySkips skips) {
        this.exceptionService = exceptionService;
        this.skips = skips;
    }

    /**
     * 誤配として記録された荷役から例外を起票する。
     *
     * <p><strong>誤配のときだけ起票する。</strong> 予定どおりの作業まで例外にすると、
     * 例外一覧が荷役の履歴になり、本当に対応が要るものが埋もれる。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HandlingActivityRegisteredEvent event) {
        if (!event.misrouted()) {
            return;
        }
        var result = exceptionService.raiseAutomatically(
                event.trackingNumber(), ExceptionType.MISROUTED,
                event.completionTime(),
                "予定ルートに無い%sが %s で記録されました。現在地から経路を引き直してください"
                        .formatted(displayNameOf(event.handlingType()), event.locationUnlocode()),
                SUBSCRIBER);
        if (result.outcome() != RaiseTrackingExceptionCommandService.Outcome.ACCEPTED) {
            // **取りこぼしを数える。** 結果整合では利用者の画面に返せないため、
            // ここが唯一「起票されなかった」ことを知る手段になる
            skips.recordSkip(SUBSCRIBER, result.outcome().name(), event.trackingNumber());
        }
    }

    /**
     * 荷役種別の表示名。
     *
     * <p><strong>Handling の列挙型を参照しない</strong>（ADR-012）。イベントが運ぶのは
     * 列挙子名の文字列であり、表示のことばは受け取る側が決める。
     */
    private static String displayNameOf(String handlingType) {
        return switch (handlingType) {
            case "LOAD" -> "積込";
            case "UNLOAD" -> "荷降し";
            default -> "作業";
        };
    }
}
