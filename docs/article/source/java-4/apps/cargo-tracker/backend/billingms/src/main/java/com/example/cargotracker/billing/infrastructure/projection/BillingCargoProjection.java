package com.example.cargotracker.billing.infrastructure.projection;

import com.example.cargotracker.billing.infrastructure.persistence.BillingCargoSnapshotMapper;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.MessageIdentifier;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 請求が読む貨物スナップショット（US21 §受入基準 2・3 の入力）。
 *
 * <p><b>引渡のイベントだけでは料金を数えられない。</b> {@code CargoDeliveredEvent} は
 * 追跡番号・予約・引渡時刻・場所しか運ばない。料金の式が要るのは<b>区間・重量・
 * 貨物種別</b>なので、{@code TrackingInitializedEvent} を購読して写す
 * （ADR-0012 と同じ形）。請求のたびに trackingms へ問い合わせない——相手が
 * 落ちている間は請求書が作れなくなる。</p>
 *
 * <p><b>重量は NULL のまま残す。</b> 重量を契約に載せたのは IT13 で、それより前に
 * 積まれたイベントには入っていない。0 で埋めると重量係数が下限に落ち、<b>足りない
 * 重量で安い請求</b>が黙って出る。NULL のままにして、算出のときに断る。</p>
 *
 * <p><b>区間は入れ直す。</b> 追記専用の行はリプレイで増える（IT6 で実際に踏んだ）。
 * 先に消してから入れる。</p>
 *
 * <p><b>処理の列を貨物ごとに分ける</b>（[ADR-0014] 決定 4）。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "trackingNumber")
@Component
public class BillingCargoProjection {

    private final BillingCargoSnapshotMapper cargos;
    private final Clock clock;

    public BillingCargoProjection(BillingCargoSnapshotMapper cargos, Clock clock) {
        this.cargos = cargos;
        this.clock = clock;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event, @MessageIdentifier String eventId) {
        cargos.upsert(new BillingCargoSnapshotMapper.SnapshotRow(
                event.trackingNumber(), event.bookingId(), event.shipperId(),
                event.originUnLocode(), event.destinationUnLocode(), event.cargoType(),
                event.weightKg(), clock.instant(), eventId));

        cargos.deleteLegs(event.trackingNumber());
        int seq = 1;
        for (TrackingInitializedEvent.Leg leg : event.legs()) {
            cargos.insertLeg(new BillingCargoSnapshotMapper.LegRow(
                    event.trackingNumber(), seq++, leg.loadUnLocode(), leg.unloadUnLocode()));
        }
    }
}
