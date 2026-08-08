package com.example.cargotracker.tracking.interfaces.events;

import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.tracking.application.internal.commandservices.RecordTrackingEventCommandService;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG =
            LoggerFactory.getLogger(TrackingHandlingEventHandler.class);

    private final RecordTrackingEventCommandService recordService;

    public TrackingHandlingEventHandler(RecordTrackingEventCommandService recordService) {
        this.recordService = recordService;
    }

    /**
     * 追跡イベントを記録し、輸送状態を進める。
     *
     * <p><strong>失敗はログに残す。</strong> 結果整合では利用者の画面に返せないため、
     * ここが唯一「反映されなかった」ことを知る手段になる。
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
            case NOT_FOUND -> LOG.warn(
                    "追跡レコードが見つからないため反映を行わない trackingNumber={}",
                    event.trackingNumber());
            case CONFLICTED -> LOG.warn(
                    "他の更新が先行したため追跡へ反映できなかった trackingNumber={}",
                    event.trackingNumber());
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
