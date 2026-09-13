package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.domain.model.events.BookingDeliveredEvent;
import com.example.cargotracker.booking.domain.model.events.BookingDeliveryRevertedEvent;
import com.example.cargotracker.booking.domain.model.events.BookingMisroutedEvent;
import com.example.cargotracker.booking.domain.model.events.BookingSettledEvent;
import com.example.cargotracker.booking.domain.model.events.BookingSettlementRevertedEvent;
import com.example.cargotracker.booking.domain.model.events.HandlingRecordedEvent;
import com.example.cargotracker.booking.domain.model.events.HandlingRevertedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 予約が輸送されてから精算されるまでを写す（US15・US16・US23・US28）。
 *
 * <p><b>{@code CargoProjection} から分けた。</b> 1 ファイルが 500 行を超えると、
 * 何を写しているファイルなのかが読めなくなる（行数の基準はそのための目安）。
 * 切り口はテスト側と揃えてある——{@code CargoHandlingProjectionIT} と
 * {@code CargoSettlementProjectionIT} が見ているのがここである。</p>
 *
 * <p><b>同じパッケージに置くことに意味がある。</b> Processing Group はパッケージ名で
 * 決まる（{@code @ProcessingGroup} は Axon 5 に無い）ので、分けた 2 つは同じ
 * Processor・同じ退避先で動き、同じ予約の順序も保たれる。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
@Component
public class CargoProgressProjection {

    private static final Logger log = LoggerFactory.getLogger(CargoProgressProjection.class);

    private final CargoSummaryMapper cargos;
    private final Clock clock;

    public CargoProgressProjection(CargoSummaryMapper cargos, Clock clock) {
        this.cargos = cargos;
        this.clock = clock;
    }

    /**
     * 最後の荷役を写す（US15 / 不変条件 12）。
     *
     * <p><b>最初の受領で予約が輸送中になる。</b> 集約と同じ判断をここに書き直さない
     * ——状態は行から読み、集約が決めた遷移に従う。</p>
     */
    @EventHandler
    public void on(HandlingRecordedEvent event) {
        var current = cargos.findById(event.bookingId());
        if (current == null) {
            return;
        }
        // 最初の受領で輸送中。以降は動かさない（集約と同じ判断）。
        String status = BookingStatus.TRACKING_ISSUED.name().equals(current.bookingStatus())
                ? BookingStatus.IN_TRANSIT.name()
                : current.bookingStatus();

        cargos.updateLastHandling(event.bookingId(), status, event.handlingType(),
                event.unLocode(), event.completedAt(), false, clock.instant());
    }

    /**
     * 引き渡しが済んだ（US16 §受入基準 4 / UC14）。
     *
     * <p><b>記録するだけでは誰にも見えない。</b> 集約が引取済になっても、ここに
     * 書き手が無ければ営業の一覧は輸送中のまま残る（IT10 のクラスタで実測）。</p>
     *
     * <p><b>書けなかったことを黙らない。</b> 戻り値を捨てると、投影に行が無いことが
     * 誰にも見えないまま「引き取ったのに反映されない」だけが残る。</p>
     */
    @EventHandler
    public void on(BookingDeliveredEvent event) {
        int updated = cargos.updateBookingStatus(event.bookingId(),
                BookingStatus.DELIVERED.name(), clock.instant());
        if (updated == 0) {
            log.warn("引き渡しを書ける予約が投影に無い: bookingId={}", event.bookingId());
        }
    }

    /**
     * 入金が済んで予約が精算済になった（US23 §受入基準 4 / UC18）。
     *
     * <p><b>これが無いと輪が閉じない。</b> 集約は {@code BookingSettledEvent} を
     * 出し、Event Processor もそれを読んでいたのに、<b>ここに書き手が無かった</b>
     * ため営業の一覧は引取済のまま残っていた（IT14 で未達。集約テストも受け入れも
     * 緑だったのは、どちらも 1 サービスの中だけを見ていたからである）。</p>
     *
     * <p><b>状態だけを書く。</b> {@code settled_at} は読み口が無いので書かない——
     * 誰も読まない列に書くと、正しいかどうかを確かめる手段が無いまま増える
     * （{@code delivered_at} が既にその形で残っている）。</p>
     *
     * <p><b>二度届いても同じ。</b> 同じ値を入れ直すだけである。</p>
     */
    @EventHandler
    public void on(BookingSettledEvent event) {
        int updated = cargos.updateBookingStatus(event.bookingId(),
                BookingStatus.SETTLED.name(), clock.instant());
        if (updated == 0) {
            log.warn("精算を書ける予約が投影に無い: bookingId={} invoiceId={}",
                    event.bookingId(), event.invoiceId());
        }
    }

    /**
     * 精算が取り消された（IT15 引き継ぎ 3）。
     *
     * <p><b>記録と打ち消しは対で出す。</b> 精算済を書いたのなら、戻す側も書く
     * ——書かなければ営業の一覧は精算済のまま残る。</p>
     */
    @EventHandler
    public void on(BookingSettlementRevertedEvent event) {
        int updated = cargos.updateBookingStatus(event.bookingId(),
                BookingStatus.DELIVERED.name(), clock.instant());
        if (updated == 0) {
            log.warn("精算の取り消しを書ける予約が投影に無い: bookingId={} invoiceId={}",
                    event.bookingId(), event.invoiceId());
        }
    }

    /**
     * 引き渡しの記録が取り消された（IT11 引き継ぎ枠 A）。
     *
     * <p><b>戻す先はイベントが運ぶ。</b> 投影がここで導き直すと、集約と投影が
     * 別々の判断を持つことになる。</p>
     */
    @EventHandler
    public void on(BookingDeliveryRevertedEvent event) {
        int updated = cargos.updateBookingStatus(event.bookingId(), event.restoredStatus(),
                clock.instant());
        if (updated == 0) {
            log.warn("引き渡しの取り消しを書ける予約が投影に無い: bookingId={}", event.bookingId());
        }
    }

    /** 予定ルート外の荷役を受けた（US28 / 不変条件 12）。 */
    @EventHandler
    public void on(BookingMisroutedEvent event) {
        cargos.updateRoutingStatus(event.bookingId(), RoutingStatus.MISROUTED.name(),
                clock.instant());
    }

    /** 取り消された荷役の分を戻す（不変条件 13）。 */
    @EventHandler
    public void on(HandlingRevertedEvent event) {
        if (event.misrouteCleared()) {
            cargos.updateRoutingStatus(event.bookingId(), RoutingStatus.ROUTED.name(),
                    clock.instant());
        }
    }
}
