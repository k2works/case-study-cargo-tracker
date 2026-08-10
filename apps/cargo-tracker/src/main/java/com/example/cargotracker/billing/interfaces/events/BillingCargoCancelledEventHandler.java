package com.example.cargotracker.billing.interfaces.events;

import com.example.cargotracker.billing.application.internal.commandservices
        .ChargeCancellationFeeCommandService;
import com.example.cargotracker.shared.domain.event.CargoCancelledEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 承認されたキャンセルからキャンセル料の請求書を作る（US30。ADR-021）。
 *
 * <p><strong>同期のポートにしない。</strong> 承認するのは追跡管理者、請求するのは
 * 経理担当者である。<strong>承認画面の前にいる人はキャンセル料について
 * 何もできない</strong>ため、その場で結果を返す必要が無い。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。承認が巻き戻ったときに
 * 請求書だけが残らない。
 *
 * <p><strong>作らなかったことは記録に残す。</strong>「例外にしない」は
 * 「記録しない」ではない（IT14 の P1）。
 */
@Component
public class BillingCargoCancelledEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "billing-cargo-cancelled";

    private final ChargeCancellationFeeCommandService chargeService;
    private final EventualConsistencySkips skips;

    public BillingCargoCancelledEventHandler(
            ChargeCancellationFeeCommandService chargeService,
            EventualConsistencySkips skips) {
        this.chargeService = chargeService;
        this.skips = skips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CargoCancelledEvent event) {
        var outcome = chargeService.charge(
                event.bookingId().toString(), event.feeRate());
        if (outcome != ChargeCancellationFeeCommandService.Outcome.CHARGED) {
            // **料率 0 は正常な結末である**（0 円の請求書は送る相手がいない）。
            // それでも残す — 「請求しなかった」ことを後から説明できるようにする
            skips.recordSkip(SUBSCRIBER, outcome.name(), event.bookingId().toString());
        }
    }
}
