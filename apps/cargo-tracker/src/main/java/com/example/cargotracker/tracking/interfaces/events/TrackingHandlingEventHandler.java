package com.example.cargotracker.tracking.interfaces.events;

import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.tracking.application.internal.commandservices.RecordTrackingEventCommandService;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 荷役の登録を追跡に反映する（US15）。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、荷役の登録が
 * 巻き戻ったときに追跡だけが進む。
 *
 * <p>荷役種別を追跡イベント種別へ翻訳するのはここである。値が同じでも
 * 「荷役として何をしたか」と「追跡の上で何が起きたか」は別の事実であり、
 * <strong>対応づけは受け取る側の仕事</strong>である。
 */
@Component
public class TrackingHandlingEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "tracking";

    private final RecordTrackingEventCommandService recordService;
    private final EventualConsistencySkips skips;

    public TrackingHandlingEventHandler(
            RecordTrackingEventCommandService recordService,
            EventualConsistencySkips skips) {
        this.recordService = recordService;
        this.skips = skips;
    }

    /**
     * 追跡イベントを記録し、輸送状態を進める。
     *
     * <p><strong>失敗は数えられる場所に出す。</strong> 結果整合では利用者の画面に
     * 返せないため、ここが唯一「反映されなかった」ことを知る手段になる。
     * ログだけでは誰も見ないため、件数として残す（ADR-009 / IT6 追補 A1）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HandlingActivityRegisteredEvent event) {
        var result = recordService.recordEvent(
                event.trackingNumber(),
                toEventType(event.handlingType()),
                event.completionTime(),
                event.locationUnlocode(),
                event.voyageNumber());

        switch (result) {
            case NOT_FOUND, CONFLICTED -> skips.recordSkip(
                    SUBSCRIBER, result.name(), event.trackingNumber());
            default -> { /* 反映できた */ }
        }
    }

    /**
     * 荷役種別を追跡イベント種別へ翻訳する。
     *
     * <p><strong>名前の一致に依存しない。</strong> 依存すると、荷役側に種別を足した瞬間に
     * コンパイルは通り、実行時に落ちる。
     */
    private static TrackingEventType toEventType(String handlingType) {
        return switch (handlingType) {
            case "RECEIVE" -> TrackingEventType.RECEIVE;
            case "LOAD" -> TrackingEventType.LOAD;
            case "UNLOAD" -> TrackingEventType.UNLOAD;
            case "CUSTOMS" -> TrackingEventType.CUSTOMS;
            case "CLAIM" -> TrackingEventType.CLAIM;
            default -> throw new IllegalArgumentException(
                    "追跡イベントに対応しない荷役種別です: " + handlingType);
        };
    }
}
