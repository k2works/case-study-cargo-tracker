package com.example.cargotracker.handling.infrastructure.projection;

import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import com.example.cargotracker.handling.domain.model.events.CustomsDeclarationRegisteredEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsStatusUpdatedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.domain.model.events.CustomsClearanceNotifiedEvent;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsStatusHistoryMapper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.axonframework.messaging.core.annotation.MessageIdentifier;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 通関申告の投影（US29 §受入基準 7）。
 *
 * <p><b>主キーは申告番号</b>（税関が採番し、利用者が持ち込む）。読み直しても
 * 行は増えない。</p>
 *
 * <p><b>履歴も写す。</b> 正典は当初「履歴は Event Store から読む」だったが、
 * <b>この版のクエリハンドラからは読めない</b>——タグを指定しても
 * {@code havingAnyTag()} でも 0 件になる（実測）。集約の復元は同じ API で動くので、
 * クエリの {@code ProcessingContext} がこの読み方を支えていない。ADR-0012 と同じ形で
 * 正典を直した。<b>主キーは元イベントの識別子</b>なので、リプレイで積み上がらない。</p>
 *
 * <p><b>留置営業日数はここでは数えない。</b> 留置中は日が経つだけで日数が変わるのに
 * イベントは来ないので、列に持つと古いままになる。列に写すのは「留置から出るとき」
 * の確定値だけで、留置中の日数は読むときに数える（{@code CustomsQueryHandler}）。</p>
 *
 * <p><b>処理の列を申告ごとに分ける。</b> 既定では列が全体で 1 本なので、1 件の毒で
 * <b>無関係の申告のイベントまで退避される</b>（IT12 のクラスタ E2E で 4 件のうち 3 件が
 * 巻き添え）。退避先は順序を守るために「同じ列の後続」も退避するので、列の切り方が
 * そのまま被害の範囲になる。同じ申告の中では順序が要る（訂正は登録より後に効かなければ
 * ならない）ので、申告より細かくは切らない。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "declarationNumber")
@Component
public class CustomsDeclarationProjection {

    private static final Logger log = LoggerFactory.getLogger(CustomsDeclarationProjection.class);

    private final CustomsDeclarationMapper declarations;
    private final CustomsStatusHistoryMapper history;
    private final Clock clock;

    public CustomsDeclarationProjection(CustomsDeclarationMapper declarations,
            CustomsStatusHistoryMapper history, Clock clock) {
        this.declarations = declarations;
        this.history = history;
        this.clock = clock;
    }

    @EventHandler
    public void on(CustomsDeclarationRegisteredEvent event, @MessageIdentifier String eventId) {
        int inserted = declarations.insert(new CustomsDeclarationMapper.CustomsDeclarationRow(
                event.declarationNumber(), event.trackingNumber(), event.bookingId(),
                CustomsStatus.PENDING.name(), event.declaredAt(),
                event.registeredAt(), null, null, null, clock.instant()));
        if (inserted == 0) {
            recordSkippedRegistration(event);
        }
        // **登録も履歴に出す**（不変条件 2「登録も含め、変更はすべてイベントとして
        // 残る」）。読む人が「いつ申告したのか」を別の画面で探さずに済む。
        history.insert(new CustomsStatusHistoryMapper.CustomsStatusHistoryRow(
                eventId, event.declarationNumber(), "REGISTERED", null,
                CustomsStatus.PENDING.name(), "通関申告を登録しました",
                event.registeredBy(), event.registeredAt(), clock.instant()));
    }

    /**
     * 状態の更新を写す。
     *
     * <p><b>留置に入った時刻だけ書き換える。</b> 留置から出るときに上書きすると、
     * 「いつから留置だったか」が消えて、あとから日数を数え直せなくなる。</p>
     */
    @EventHandler
    public void on(CustomsStatusUpdatedEvent event, @MessageIdentifier String eventId) {
        CustomsStatus status = CustomsStatus.valueOf(event.status());
        history.insert(new CustomsStatusHistoryMapper.CustomsStatusHistoryRow(
                eventId, event.declarationNumber(), "STATUS_CHANGED", event.previousStatus(),
                event.status(), event.reason(), event.changedBy(), event.changedAt(),
                clock.instant()));
        declarations.updateStatus(new CustomsDeclarationMapper.CustomsStatusChange(
                event.declarationNumber(), event.status(), event.reason(), event.changedBy(),
                status == CustomsStatus.HELD ? event.changedAt() : null,
                event.changedAt(), clock.instant()));
    }

    /**
     * 通関完了を知らせた記録（US29 §受入基準 4）。
     *
     * <p><b>記録と読み口は対で出す。</b> 記録だけを積んで読み口を出さないと、
     * 受入基準の満たし方そのものが成り立たない（IT10 で 2 回踏んだ）。</p>
     */
    @EventHandler
    public void on(CustomsClearanceNotifiedEvent event, @MessageIdentifier String eventId) {
        history.insert(new CustomsStatusHistoryMapper.CustomsStatusHistoryRow(
                eventId, event.declarationNumber(), "CLEARANCE_NOTIFIED", null, null,
                event.content(), null, event.notifiedAt(), clock.instant()));
    }

    /**
     * 行が増えなかったときに、その理由を残す。
     *
     * <p><b>黙って見送らない。</b> {@code ON CONFLICT DO NOTHING} は 2 つの索引を
     * まとめて受け止めるので、リプレイ（同じ申告を読み直した）と不変条件 3 の違反
     * （同じ貨物に未決着の申告がもう 1 件できた）が同じ 0 件に見える。読み直して
     * 区別し、違反のほうだけ警告に出す——**登録した本人には成功に見えている**ので、
     * 気づく手段がここにしか無い。</p>
     */
    private void recordSkippedRegistration(CustomsDeclarationRegisteredEvent event) {
        if (declarations.findByNumber(event.declarationNumber()) != null) {
            return; // 同じ申告の読み直し。行は既にある。
        }
        var unsettled = declarations.findUnsettledByCargo(event.trackingNumber());
        log.warn("通関申告 {} を投影できませんでした。貨物 {} には未決着の申告 {} が既にあります"
                        + "（不変条件 3）。申告は登録されていますが一覧には出ません",
                event.declarationNumber(), event.trackingNumber(),
                unsettled.isEmpty() ? "(不明)" : unsettled.getFirst().declarationNumber());
    }
}
