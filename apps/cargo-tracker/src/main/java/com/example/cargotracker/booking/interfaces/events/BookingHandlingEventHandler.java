package com.example.cargotracker.booking.interfaces.events;

import com.example.cargotracker.booking.application.internal.commandservices.ApplyHandlingResultCommandService;
import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG =
            LoggerFactory.getLogger(BookingHandlingEventHandler.class);

    /** 最初の積込で輸送が始まる（遷移表 #6）。 */
    private static final String LOAD = "LOAD";

    private final ApplyHandlingResultCommandService applyService;

    public BookingHandlingEventHandler(ApplyHandlingResultCommandService applyService) {
        this.applyService = applyService;
    }

    /**
     * 誤配と輸送開始を反映する。
     *
     * <p><strong>失敗はログに残す。</strong> 結果整合では利用者の画面に返せない。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HandlingActivityRegisteredEvent event) {
        var result = applyService.apply(
                event.bookingId(), event.misrouted(), LOAD.equals(event.handlingType()));

        switch (result) {
            case NOT_FOUND -> LOG.warn(
                    "予約が見つからないため反映を行わない bookingId={}", event.bookingId());
            case CONFLICTED -> LOG.warn(
                    "他の更新が先行したため予約へ反映できなかった bookingId={}",
                    event.bookingId());
            default -> { /* 反映できた */ }
        }
    }
}
