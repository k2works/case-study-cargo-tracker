package com.example.cargotracker.handling.domain.model.aggregates;

import com.example.cargotracker.handling.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.commands.VoidHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.time.Clock;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 港での荷役作業 1 件（UC13 / US15）。<b>1 作業 1 集約</b>。
 *
 * <p>履歴は投影が持つ（`handling_activity`）。集約に履歴を持たせると、1 貨物に
 * 何十件も積み上がったイベント列を毎回復元することになる。</p>
 *
 * <p><b>予定ルート外でも記録を拒まない</b>（不変条件 2）。現場ではすでに作業が
 * 終わっており、記録できないと事実が失われる。<b>警告して残す</b>。</p>
 *
 * <p><b>冪等キーはクライアントが作る</b>（不変条件 5）。同じ {@code activityId} の
 * 再送は二重に記録しない。通信断で再送しても現場が困らないようにする。</p>
 */
@EventSourced(idType = String.class, tagKey = "activityId")
public class HandlingActivity {

    private String activityId;
    private String trackingNumber;
    private HandlingType type;
    private boolean voided;

    @EntityCreator
    public HandlingActivity() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 荷役を記録する（US15 §受入基準 2・3・4）。
     *
     * <p><b>同じ活動 ID の再送は黙って通す</b>（不変条件 5）。断ると、通信断で
     * 再送した現場に「二重に記録された」と誤解させる。イベントは足さない。</p>
     *
     * <p><b>未来の時刻は拒む</b>（不変条件 6）。過去は通す——通信不能時は紙に
     * 控えて後から入れる運用がある。</p>
     */
    @CommandHandler
    public String register(RegisterHandlingActivityCommand command, EventAppender appender,
            Clock clock) {
        if (activityId != null) {
            // 再送。すでに記録してあるので、同じ応答を返して終える。
            return activityId;
        }
        requireText(command.trackingNumber(), "追跡番号は必須です");
        requireText(command.bookingId(), "予約 ID は必須です");
        requireText(command.unLocode(), "作業場所は必須です");
        requireText(command.operator(), "作業者は必須です");
        if (command.type() == null) {
            throw new BusinessRuleViolation("作業種別は必須です");
        }
        if (command.completedAt() == null) {
            throw new BusinessRuleViolation("作業日時は必須です");
        }

        Instant now = clock.instant();
        if (command.completedAt().isAfter(now)) {
            // 未来の作業は起きていない。過去は通す（後から入れる運用がある）。
            throw new BusinessRuleViolation("作業日時に未来は指定できません");
        }
        // 要件は種別自身が持つ。呼び出し側に種別ごとの分岐を書かせない。
        if (command.type().requiresVoyageNumber()
                && (command.voyageNumber() == null || command.voyageNumber().isBlank())) {
            throw new BusinessRuleViolation(
                    command.type().label() + "には航海番号が必要です");
        }

        appender.append(new HandlingActivityRegisteredEvent(command.activityId(),
                command.trackingNumber(), command.bookingId(), command.type().name(),
                command.unLocode(), command.voyageNumber(), command.offRoute(),
                command.finalPort(), command.operator(), command.completedAt(), now));
        return command.activityId();
    }

    /**
     * 記録を取り消す（不変条件 7）。
     *
     * <p><b>元の記録は残る。</b> 取り消した事実が増えるだけで、現場で起きたことを
     * 後から無かったことにはしない。</p>
     */
    @CommandHandler
    public void voidActivity(VoidHandlingActivityCommand command, EventAppender appender,
            Clock clock) {
        if (activityId == null) {
            throw new IllegalTransition("荷役 " + command.activityId() + " は記録されていません");
        }
        if (voided) {
            throw new IllegalTransition("荷役 " + activityId + " はすでに取り消されています");
        }
        requireText(command.reason(), "取り消しの理由は必須です");
        requireText(command.voidedBy(), "取り消した人は必須です");

        appender.append(new HandlingActivityVoidedEvent(activityId, trackingNumber,
                bookingId, type.name(), command.reason().trim(), command.voidedBy(),
                clock.instant()));
    }

    private String bookingId;

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(message);
        }
    }

    @EventSourcingHandler
    void on(HandlingActivityRegisteredEvent event) {
        this.activityId = event.activityId();
        this.trackingNumber = event.trackingNumber();
        this.bookingId = event.bookingId();
        this.type = HandlingType.valueOf(event.handlingType());
    }

    @EventSourcingHandler
    void on(HandlingActivityVoidedEvent event) {
        this.voided = true;
    }

    /** 復元した種別。取り消しのイベントに載せる。 */
    public HandlingType type() {
        return type;
    }

    /** 取り消し済みか。二度目の取り消しを断るのに使う。 */
    public boolean voided() {
        return voided;
    }
}
