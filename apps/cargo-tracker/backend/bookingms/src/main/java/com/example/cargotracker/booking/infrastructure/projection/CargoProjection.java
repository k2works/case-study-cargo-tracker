package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 貨物予約の投影（Processing Group: booking-cargo-projection）。
 *
 * <p>荷主名を非正規化して持つ。一覧が JOIN しないため（data-model.md）。荷主が
 * 見つからないときは {@code null} のまま書く。予約そのものは受け付けられている
 * ので、荷主の投影が遅れていることを理由に予約を落とさない。</p>
 *
 * <p><b>投影はコマンドを送らない。</b> 送るとリプレイのたびに副作用が再実行される。</p>
 */
@Component
public class CargoProjection {

    private static final Logger log = LoggerFactory.getLogger(CargoProjection.class);

    private final CargoSummaryMapper cargos;
    private final ShipperMapper shippers;
    private final AttentionItemRecorder attentionItems;
    private final Clock clock;

    public CargoProjection(CargoSummaryMapper cargos, ShipperMapper shippers,
            AttentionItemRecorder attentionItems, Clock clock) {
        this.cargos = cargos;
        this.shippers = shippers;
        this.attentionItems = attentionItems;
        this.clock = clock;
    }

    @EventHandler
    public void on(CargoBookedEvent event) {
        Instant now = clock.instant();
        // 業務日付で採番する。UTC で採ると、日本時間の朝 9 時より前に受け付けた
        // 予約の番号が前日の日付になる。
        LocalDate bookedOn = LocalDate.ofInstant(now, clock.getZone());

        ShipperMapper.ShipperRow shipper = shippers.findById(event.shipperId());

        cargos.insert(new CargoSummaryMapper.CargoSummaryRow(
                event.bookingId(),
                cargos.nextBookingNumber(bookedOn),
                event.shipperId(),
                shipper == null ? null : shipper.name(),
                null,
                event.originUnLocode(),
                event.destinationUnLocode(),
                event.arrivalDeadline(),
                event.cargoType(),
                event.weightKg(),
                event.lengthCm(),
                event.widthCm(),
                event.heightCm(),
                event.quantity(),
                event.productName(),
                event.hazardImoClass(),
                event.hazardUnNumber(),
                event.temperatureMinC(),
                event.temperatureMaxC(),
                BookingStatus.PRELIMINARY.name(),
                RoutingStatus.NOT_ROUTED.name(),
                now,
                // 引き渡しはまだ。受け付けた時点で入れると、放置の判断ができない。
                null,
                // 受け付けた時点では修正されていない。受付日時を入れると
                // 「一度も直していない予約」と「直した予約」が見分けられない。
                null,
                null,
                now,
                null));
    }

    /**
     * 経路設計を依頼した（US06）。予約と経路設計の状態を進める。
     *
     * <p>状態は集約のイベントだけが書く。画面のボタン出し分けはこの値を読むが、
     * 判定は書き直さず {@code BookingStatus} の述語を呼ぶ。</p>
     *
     * <p><b>更新できなかったことを黙らない。</b> 戻り値を捨てると、対象の行が
     * 無かったこと（投影の取りこぼし・順序の入れ替わり）が誰にも見えないまま
     * 残り、経路設計者の一覧に出ないだけになる。</p>
     */
    @EventHandler
    public void on(RoutingRequestedEvent event) {
        Instant now = clock.instant();
        int updated = cargos.updateRoutingRequested(event.bookingId(),
                BookingStatus.ROUTE_PROPOSED.name(),
                RoutingStatus.ROUTING_REQUESTED.name(),
                now,
                now);
        if (updated == 0) {
            log.warn("経路設計の依頼を書ける予約が投影に無い: bookingId={}", event.bookingId());
        }
    }

    /**
     * 仮受付の予約情報の修正（US32）。
     *
     * <p>状態は動かさない。仮受付のまま内容だけが差し替わる。</p>
     *
     * <p><b>更新できなかったことを黙らない。</b> 戻り値を捨てると、投影に行が無い
     * ことが誰にも見えないまま「直したのに反映されない」だけが残る。</p>
     */
    @EventHandler
    public void on(CargoSpecificationUpdatedEvent event) {
        Instant now = clock.instant();
        int updated = cargos.updateSpecification(new CargoSummaryMapper.CargoSummaryRow(
                event.bookingId(), null, null, null, null,
                event.originUnLocode(), event.destinationUnLocode(), event.arrivalDeadline(),
                event.cargoType(), event.weightKg(), event.lengthCm(), event.widthCm(),
                event.heightCm(), event.quantity(), event.productName(),
                event.hazardImoClass(), event.hazardUnNumber(),
                event.temperatureMinC(), event.temperatureMaxC(),
                // 状態と受付日時は UPDATE 文が触らない。行の値をここで作り直すと、
                // 受付日時が修正のたびに動く。
                null, null, null, null,
                // 「いつ直したか」はイベントが持つ。ここで現在時刻を書くと、
                // 読み直しのたびに最終更新が動く。
                event.updatedAt(), event.updatedBy(), now, null));

        if (updated == 0) {
            log.warn("修正を書ける予約が投影に無い: bookingId={}", event.bookingId());
            attentionItems.add("PROJECTION_REJECTED", "BOOKING", event.bookingId(),
                    "ROLE_SALES", "修正の対象が投影に無い", "{}", now);
        }
    }

    /** 業務タイムゾーン。Clock が持つ（BusinessClockConfiguration）。 */
    ZoneId zone() {
        return clock.getZone();
    }
}
