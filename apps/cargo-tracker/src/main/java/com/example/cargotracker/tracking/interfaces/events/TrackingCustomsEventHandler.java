package com.example.cargotracker.tracking.interfaces.events;

import com.example.cargotracker.shared.domain.event.CustomsStatusChangedEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.tracking.application.internal.commandservices
        .RaiseTrackingExceptionCommandService;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通関の留置を例外として起票する（US29）。
 *
 * <p><strong>これが {@code domain-model.md} のビジネスルール 4 の置き換えである。</strong>
 * 旧版は「CUSTOMS_HOLD は税関システムから自動登録される」と書いていたが、
 * ADR-006（外部システムとは連携しない）と矛盾していた。実際に起票するのは
 * <strong>担当者が通関状態を「留置」に更新したとき</strong>である。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、
 * 通関の更新が巻き戻ったときに例外だけが残る。
 *
 * <p><strong>Handling から Tracking を呼ばない</strong>（ADR-012）。運ばれるのは
 * 「留置になった」という事実であり、例外として起票するかは Tracking が決める。
 */
@Component
public class TrackingCustomsEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "tracking-customs";

    private final RaiseTrackingExceptionCommandService exceptionService;
    private final EventualConsistencySkips skips;

    public TrackingCustomsEventHandler(
            RaiseTrackingExceptionCommandService exceptionService,
            EventualConsistencySkips skips) {
        this.exceptionService = exceptionService;
        this.skips = skips;
    }

    /**
     * 対応が要る状態（留置・不可）になったら税関保留の例外を起票する。
     *
     * <p><strong>通関完了では起票しない。</strong> 完了は荷主への通知として
     * Booking が記録する。両方で起票すると、同じ出来事が例外と通知の 2 つになる。
     *
     * <p><strong>不可も対象にする。</strong> 留置は保管料だが、不可は積戻し・廃棄・
     * 関税の争いに発展する。留置だけを拾うと、最も重い状態が最も静かになる。
     * <strong>どの状態が対応を要するかはイベントが運ぶ</strong>（Handling の列挙型を
     * 参照しない。ADR-012）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CustomsStatusChangedEvent event) {
        if (!event.held()) {
            return;
        }
        var result = exceptionService.raiseAutomatically(
                event.trackingNumber(), ExceptionType.CUSTOMS_HOLD,
                event.changedAt(),
                "通関の状態が「%s」になりました（申告番号 %s）。理由: %s"
                        .formatted(event.statusLabel(), event.declarationNumber(),
                                event.reason()),
                event.changedBy());
        if (result.outcome() != RaiseTrackingExceptionCommandService.Outcome.ACCEPTED) {
            // **取りこぼしを数える。** 結果整合では利用者の画面に返せないため、
            // ここが唯一「起票されなかった」ことを知る手段になる
            skips.recordSkip(SUBSCRIBER, result.outcome().name(), event.trackingNumber());
        }
    }
}
