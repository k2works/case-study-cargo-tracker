package com.example.cargotracker.handling.domain.model.aggregates;

import com.example.cargotracker.handling.domain.model.commands.RegisterCustomsDeclarationCommand;
import com.example.cargotracker.handling.domain.model.commands.UpdateCustomsStatusCommand;
import com.example.cargotracker.handling.domain.model.events.CustomsClearanceNotifiedEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsDeclarationRegisteredEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsStatusUpdatedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.domain.model.valueobjects.HolidayCalendar;
import com.example.cargotracker.shared.contract.event.CustomsStatusChangedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.shared.domain.location.CountryCode;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 輸入港での通関申告 1 件（UC21 / US29）。<b>1 申告 1 集約</b>。
 *
 * <p><b>識別子は申告番号。</b> 採番するのは税関で、利用者が持ち込む。だから書式は
 * 検査しない（不変条件 1）——国ごとに違うものを、こちらの想像で縛らない。</p>
 *
 * <p><b>「未決着は貨物あたり高々 1 件」（不変条件 3）はここでは守れない。</b>
 * 1 申告 1 集約なので、他の申告を知らない。application 層が守る——IT9 の
 * 「5 分以内の同じ記録を断る」と同じ形である。</p>
 *
 * <p><b>通関は荷役ではない。</b> {@code HandlingActivity} に混ぜない。通関は税関という
 * 別の当事者との手続きで、種別を 1 つ足して済む話ではない（要素表の注記）。</p>
 */
@EventSourced(idType = String.class, tagKey = "declarationNumber")
public class CustomsDeclaration {

    private String declarationNumber;
    private String trackingNumber;
    private String bookingId;
    private CustomsStatus status;
    /** 最新の {@code HELD} 遷移日時。留置営業日数の起点（不変条件 4）。 */
    private Instant lastHeldAt;

    @EntityCreator
    public CustomsDeclaration() {
        // Axon がイベント再生で呼ぶ。
    }

    /** 通関申告を登録する（US29 §受入基準 1。初期状態は審査中）。 */
    @CommandHandler
    public String register(RegisterCustomsDeclarationCommand command, EventAppender appender,
            Clock clock) {
        if (declarationNumber != null) {
            // 税関が採番した番号は 1 つの申告を指す。同じ番号の 2 度目は、
            // 打ち間違いか別の申告のどちらかで、どちらも黙って通してよくない。
            throw new IllegalTransition("申告番号 " + command.declarationNumber()
                    + " はすでに登録されています");
        }
        requireText(command.declarationNumber(), "申告番号は必須です");
        requireText(command.trackingNumber(), "追跡番号は必須です");
        requireText(command.bookingId(), "予約 ID は必須です");
        requireText(command.registeredBy(), "登録者は必須です");
        if (command.declaredAt() == null) {
            throw new BusinessRuleViolation("申告日時は必須です");
        }

        appender.append(new CustomsDeclarationRegisteredEvent(command.declarationNumber(),
                command.trackingNumber(), command.bookingId(), command.declaredAt(),
                command.registeredBy(), clock.instant()));
        return command.declarationNumber();
    }

    /**
     * 通関状態を更新する（US29 §受入基準 2・4・5）。
     *
     * <p><b>理由は必須</b>（不変条件 2）。履歴はイベント列そのものなので、
     * 落とすとどこにも残らない。</p>
     *
     * <p><b>内部イベントと契約の 2 本を出す。</b> 前者で自分を復元し、後者を他 BC が
     * 読む。1 本にすると、集約の都合で形を変えたいときに購読側を巻き込む。</p>
     */
    @CommandHandler
    public void updateStatus(UpdateCustomsStatusCommand command, EventAppender appender,
            Clock clock) {
        if (declarationNumber == null) {
            throw new IllegalTransition("申告 " + command.declarationNumber() + " がありません");
        }
        if (command.status() == null) {
            throw new BusinessRuleViolation("通関状態は必須です");
        }
        requireText(command.reason(), "通関状態の更新には理由が必要です");
        requireText(command.changedBy(), "変更者は必須です");
        if (!status.unsettled()) {
            // 決着したものは動かさない。出し直しは新しい申告番号で行う——
            // 税関がもう一度採番するので、こちらで使い回す番号が無い。
            throw new IllegalTransition("通関状態が " + status.label()
                    + " の申告は更新できません");
        }
        if (command.status() == status) {
            // 履歴に意味の無い行を積まない。読む人が「何が変わったのか」を
            // 探すことになる。
            throw new IllegalTransition("通関状態はすでに " + status.label() + " です");
        }

        Instant now = clock.instant();
        // **appender.append() は即座に自分へ適用される。** 留置日数は「留置から
        // 出るとき」の値なので、append の前に数えておく（IT11 で同型の欠陥を作った）。
        int heldBusinessDays = heldBusinessDaysAt(now);
        CustomsStatus previous = status;

        appender.append(new CustomsStatusUpdatedEvent(declarationNumber, previous.name(),
                command.status().name(), command.reason(), command.changedBy(), now));
        appender.append(new CustomsStatusChangedEvent(declarationNumber, trackingNumber,
                bookingId, previous.name(), command.status().name(), command.reason(),
                heldBusinessDays, command.changedBy(), now));

        if (command.status() == CustomsStatus.CLEARED) {
            // **記録と読み口は対で出す**（US29 §受入基準 4）。送信基盤はスコープ外
            // なので、伝えたという事実を残して申告詳細（S53）の履歴に出す。
            appender.append(new CustomsClearanceNotifiedEvent(declarationNumber, trackingNumber,
                    "通関が完了しました（申告番号 " + declarationNumber + "）", now));
        }
    }

    /**
     * 留置してからの営業日数（不変条件 4）。留置していなければ 0。
     *
     * <p>日付単位・業務タイムゾーンで数える。時刻を持ち込むと、同じ日の中で
     * 「3 日超」になったりならなかったりする。</p>
     *
     * <p><b>「3 営業日超」の判定はここに置かない。</b> 督促の一覧（S52）は多数の
     * 申告を投影から読み、集約を 1 件ずつ復元しない。判定を両方に置くと本番と
     * 検査が別の判定を持つことになるので、閾値は {@code CustomsQueryHandler} が
     * 単独で持つ（注 N4）。ここは記録の時点で載せる日数だけを数える。</p>
     */
    public int heldBusinessDaysAt(Instant today) {
        if (status != CustomsStatus.HELD || lastHeldAt == null) {
            return 0;
        }
        return calendar().businessDaysBetween(businessDate(lastHeldAt), businessDate(today));
    }

    /**
     * 港の所在国のカレンダー。
     *
     * <p><b>いまは輸入国を追跡番号から引けない</b>ので、業務タイムゾーンの国
     * （日本）を使う。輸入港の国コードを持ち込むには {@code CargoSnapshot} の
     * 目的港が要り、それは application 層の仕事である（IT13 で渡す）。
     * <b>足りないことは「早く点く」側に倒れる</b>ので、督促は手遅れにならない。</p>
     */
    private static HolidayCalendar calendar() {
        return HolidayCalendar.of(new CountryCode("JP"));
    }

    private static LocalDate businessDate(Instant at) {
        return LocalDate.ofInstant(at, BusinessClockConfiguration.BUSINESS_ZONE);
    }

    @EventSourcingHandler
    public void on(CustomsDeclarationRegisteredEvent event) {
        this.declarationNumber = event.declarationNumber();
        this.trackingNumber = event.trackingNumber();
        this.bookingId = event.bookingId();
        this.status = CustomsStatus.PENDING;
    }

    @EventSourcingHandler
    public void on(CustomsStatusUpdatedEvent event) {
        this.status = CustomsStatus.valueOf(event.status());
        if (this.status == CustomsStatus.HELD) {
            this.lastHeldAt = event.changedAt();
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(message);
        }
    }
}
