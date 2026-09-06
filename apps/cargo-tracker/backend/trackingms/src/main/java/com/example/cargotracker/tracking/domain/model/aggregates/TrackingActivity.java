package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Clock;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 貨物の追跡（UC12 / US14）。<b>trackingms の最初の集約</b>。
 *
 * <p>bookingms が追跡番号を発行すると、{@code BookingReactionHandler} が
 * {@link InitializeTrackingCommand}（契約コマンド）を送って追跡が始まる。</p>
 *
 * <p><b>状態は載って来ない。</b> 追跡を始めた直後がどの状態か（{@code NOT_RECEIVED}）は
 * ここが決める。送る側が相手の状態機械を知っていることにしない。</p>
 *
 * <p><b>不変条件: 二重に開始しない。</b> 連鎖は失敗したら再試行するので、同じコマンドが
 * 2 度届きうる。追跡が 2 つできると、荷役がどちらに付くのか決まらない。</p>
 */
@EventSourced(idType = String.class, tagKey = "trackingNumber")
public class TrackingActivity {

    private TrackingNumber trackingNumber;
    private String bookingId;
    private TransportStatus status;

    @EntityCreator
    public TrackingActivity() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 追跡を開始する（US14 §受入基準 3）。
     *
     * <p><b>static ではなくインスタンスのハンドラにする。</b> 両方置くと、集約が既に
     * 存在しても static のほうが呼ばれ、2 度目の開始が通る（bookingms の IT2 で実測）。</p>
     *
     * <p><b>開始した時刻は {@code Clock} で決める。</b> 発行時刻（{@code issuedAt}）を
     * そのまま使うと、連鎖が数時間止まっていたときに「止まっていなかった」ように見える。</p>
     */
    @CommandHandler
    public String initialize(InitializeTrackingCommand command, EventAppender appender,
            Clock clock) {
        if (trackingNumber != null) {
            throw new IllegalTransition(
                    "追跡 " + trackingNumber.value() + " は既に開始しています");
        }
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            // 誰の荷物か辿れない追跡は、荷役を記録しても業務に繋がらない。
            throw new BusinessRuleViolation("予約 ID は必須です");
        }
        if (command.legs().isEmpty()) {
            // 追跡番号は経路が決まってから発行される。旅程が無いのは、途中で
            // 落としたということ（IT6 の「値は全層を生き延びるか確かめる」）。
            throw new BusinessRuleViolation("旅程は必須です");
        }
        // 追跡番号そのものの検査は値オブジェクトが持つ（空なら断る）。
        TrackingNumber number = TrackingNumber.of(command.trackingNumber());

        appender.append(new TrackingInitializedEvent(number.value(), command.bookingId(),
                clock.instant()));
        return number.value();
    }

    @EventSourcingHandler
    void on(TrackingInitializedEvent event) {
        this.trackingNumber = TrackingNumber.of(event.trackingNumber());
        this.bookingId = event.bookingId();
        this.status = TransportStatus.NOT_RECEIVED;
    }

    /** 復元した輸送状態。荷役（US15・IT9）が読む。 */
    public TransportStatus status() {
        return status;
    }

    /** 元の予約。荷主向けの追跡（US18・IT8）が読む。 */
    public String bookingId() {
        return bookingId;
    }
}
