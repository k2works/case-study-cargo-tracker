package com.example.cargotracker.handling.infrastructure.projection;

import com.example.cargotracker.handling.domain.model.events.CustomsDeclarationRegisteredEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsStatusUpdatedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.domain.model.events.CustomsClearanceNotifiedEvent;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsStatusHistoryMapper;
import java.time.Clock;
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
 */
@Component
public class CustomsDeclarationProjection {

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
        declarations.insert(new CustomsDeclarationMapper.CustomsDeclarationRow(
                event.declarationNumber(), event.trackingNumber(), event.bookingId(),
                CustomsStatus.PENDING.name(), event.declaredAt(),
                event.registeredAt(), null, 0, null, null, clock.instant()));
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
        declarations.updateStatus(event.declarationNumber(), event.status(), event.reason(),
                event.changedBy(),
                // 留置から出るときの確定値は契約イベントが持つが、投影は内部イベント
                // だけを読む（契約は他 BC のもの）。ここでは 0 のままにして、
                // 数えるのは読むときにする——判定を 2 か所に置かない。
                0,
                status == CustomsStatus.HELD ? event.changedAt() : null,
                event.changedAt(), clock.instant());
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
}
