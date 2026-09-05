package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.booking.domain.service.CargoSpecificationDiff;
import com.example.cargotracker.booking.infrastructure.persistence.CargoLegMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoRevisionMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    private final CargoRevisionMapper revisions;
    private final CargoLegMapper legs;
    private final ShipperMapper shippers;
    private final AttentionItemRecorder attentionItems;
    private final Clock clock;

    public CargoProjection(CargoSummaryMapper cargos, CargoRevisionMapper revisions,
            CargoLegMapper legs, ShipperMapper shippers, AttentionItemRecorder attentionItems,
            Clock clock) {
        this.cargos = cargos;
        this.revisions = revisions;
        this.legs = legs;
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
        // 何を変えたかは、書き換える前の行としか比べられない（US32 §受入基準 4）。
        // 更新してから読むと、before が after と同じになる。
        CargoSummaryMapper.CargoSummaryRow before = cargos.findById(event.bookingId());
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

        if (before != null) {
            recordRevision(before, event);
        }

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

    /**
     * 経路が決まった（US09）。
     *
     * <p><b>区間は全行を入れ替える</b>（data-model.md）。足すだけにすると、経路を
     * 設計し直した予約に古い区間が残り、旅程が二重に見える。短くなった旅程では、
     * 行かないはずの港が残る。</p>
     *
     * <p>{@code booking_status} は動かさない。荷主に通知するまでは提案中（US12）。</p>
     */
    @EventHandler
    public void on(CargoRoutedEvent event) {
        Instant now = clock.instant();
        int updated = cargos.updateRoutingStatus(event.bookingId(),
                RoutingStatus.ROUTED.name(), now);
        if (updated == 0) {
            log.warn("経路を書ける予約が投影に無い: bookingId={}", event.bookingId());
            attentionItems.add("PROJECTION_REJECTED", "BOOKING", event.bookingId(),
                    "ROLE_ROUTING", "経路の対象が投影に無い", "{}", now);
            return;
        }

        legs.deleteByBooking(event.bookingId());
        for (int i = 0; i < event.legs().size(); i++) {
            CargoRoutedEvent.Leg leg = event.legs().get(i);
            legs.insert(new CargoLegMapper.CargoLegRow(
                    event.bookingId(), i + 1, leg.voyageNumber(),
                    leg.loadUnLocode(), leg.unloadUnLocode(),
                    leg.loadTime(), leg.unloadTime()));
        }
    }

    /**
     * 何を変えたかを残す（US32 §受入基準 4）。
     *
     * <p>変わっていなければ 1 行も書かない。「修正した」とだけ残っていて中身が空の
     * 履歴は、読む側に何も伝えない。</p>
     *
     * <p>行は修正イベントから決まりきった形で導くので、<b>リプレイで増えない</b>
     * （主キーに修正時刻を含め、{@code ON CONFLICT DO NOTHING} で入れる）。</p>
     */
    private void recordRevision(CargoSummaryMapper.CargoSummaryRow before,
            CargoSpecificationUpdatedEvent event) {
        List<CargoSpecificationDiff.FieldChange> changes = CargoSpecificationDiff.between(
                new CargoSpecificationDiff.CargoSnapshot(
                        before.originUnlocode(), before.destinationUnlocode(),
                        before.arrivalDeadline(), before.cargoType(), before.weightKg(),
                        before.lengthCm(), before.widthCm(), before.heightCm(),
                        before.quantity(), before.productName(), before.hazardImoClass(),
                        before.hazardUnNumber(), before.temperatureMinC(),
                        before.temperatureMaxC()),
                new CargoSpecificationDiff.CargoSnapshot(
                        event.originUnLocode(), event.destinationUnLocode(),
                        event.arrivalDeadline(), event.cargoType(), event.weightKg(),
                        event.lengthCm(), event.widthCm(), event.heightCm(),
                        event.quantity(), event.productName(), event.hazardImoClass(),
                        event.hazardUnNumber(), event.temperatureMinC(),
                        event.temperatureMaxC()));

        for (int i = 0; i < changes.size(); i++) {
            CargoSpecificationDiff.FieldChange change = changes.get(i);
            revisions.insert(new CargoRevisionMapper.CargoRevisionRow(
                    event.bookingId(), event.updatedAt(), change.label(), i + 1,
                    change.before(), change.after(), event.updatedBy()));
        }
    }
}
