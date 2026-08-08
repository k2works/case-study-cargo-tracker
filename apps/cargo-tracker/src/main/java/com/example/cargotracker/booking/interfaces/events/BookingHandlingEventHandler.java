package com.example.cargotracker.booking.interfaces.events;

import com.example.cargotracker.booking.application.internal.commandservices.ApplyHandlingResultCommandService;
import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 荷役の登録を予約に反映する（US15）。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。何を反映するかの判断は
 * {@link ApplyHandlingResultCommandService}（予約のことば）が持つ。
 * ここでするのは、イベントを渡して結果を記録に残すことだけである。
 */
@Component
public class BookingHandlingEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "booking";

    private final ApplyHandlingResultCommandService applyService;
    private final EventualConsistencySkips skips;

    public BookingHandlingEventHandler(
            ApplyHandlingResultCommandService applyService,
            EventualConsistencySkips skips) {
        this.applyService = applyService;
        this.skips = skips;
    }

    /**
     * 誤配と輸送開始を反映する。
     *
     * <p><strong>失敗は数えられる場所に出す。</strong> 結果整合では利用者の画面に
     * 返せないため、ここが唯一「反映されなかった」ことを知る手段になる。
     * ログだけでは誰も見ないため、件数として残す（ADR-009 / IT6 追補 A1）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HandlingActivityRegisteredEvent event) {
        // **どの種別が何を意味するかは予約が決める**（ADR-009）。
        // ここでするのは、起きた事実をそのまま渡すことだけである
        var result = applyService.apply(
                event.bookingId(), event.misrouted(), event.handlingType());

        switch (result) {
            case NOT_FOUND, CONFLICTED -> skips.recordSkip(
                    SUBSCRIBER, result.name(), String.valueOf(event.bookingId()));
            default -> { /* 反映できた */ }
        }
    }
}
