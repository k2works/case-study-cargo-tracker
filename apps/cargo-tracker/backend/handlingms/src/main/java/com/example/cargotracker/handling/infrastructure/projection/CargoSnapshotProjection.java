package com.example.cargotracker.handling.infrastructure.projection;

import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import com.example.cargotracker.shared.contract.event.CargoCancelledEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.axonframework.messaging.core.annotation.MessageIdentifier;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 貨物の写し（ACL の読み取りモデル / US15）。
 *
 * <p><b>契約イベントから作る</b>（[ADR-0012]）。`TrackingInitializedEvent` は
 * 荷役が要る値をすべて持つ——追跡番号・予約 ID・端点・貨物種別・旅程。</p>
 *
 * <p><b>Booking / Tracking の型を持ち込まない。</b> 契約は文字列・数値・日付だけを
 * 運び、handlingms は自分の形に組み直す。</p>
 *
 * <p><b>リプレイで行が増えない。</b> 追跡番号が主キーなので上書きになり、旅程は
 * 先に消してから入れ直す（追記だけにすると、リプレイで区間が倍になる）。</p>
 *
 * <p><b>処理の列を貨物ごとに分ける。</b> 既定では列が全体で 1 本なので、1 件の毒で
 * <b>無関係の貨物のイベントまで退避される</b>（IT12 のクラスタ E2E で 4 件のうち 3 件が
 * 巻き添え）。退避先は順序を守るために「同じ列の後続」も退避するので、列の切り方が
 * そのまま被害の範囲になる。同じ貨物の中では順序が要る（訂正は登録より後に効かなければ
 * ならない）ので、貨物より細かくは切らない。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "trackingNumber")
@Component
public class CargoSnapshotProjection {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CargoSnapshotProjection.class);

    private final CargoSnapshotMapper cargos;
    private final Clock clock;

    public CargoSnapshotProjection(CargoSnapshotMapper cargos, Clock clock) {
        this.cargos = cargos;
        this.clock = clock;
    }

    /**
     * 予約がキャンセルされた（US30 / ADR-0012 決定 3。IT15 T7）。
     *
     * <p><b>行は消さない。</b> 消すと、記録済みの荷役が「どの貨物のものか」を
     * 辿れなくなる。読み口は {@code cancelled = FALSE} で絞っているので、
     * 印を付けるだけで<b>荷役の作業一覧から外れる</b>——現場が止まった貨物を
     * 積み続けるのを防ぐ。</p>
     *
     * <p><b>写しが無くても止めない。</b> 追跡が始まる前にキャンセルされた貨物は
     * handlingms に写しが無い。例外にすると Event Processor が止まり、無関係の
     * 貨物のイベントまで退避される。</p>
     */
    @EventHandler
    public void on(CargoCancelledEvent event) {
        int updated = cargos.markCancelled(event.bookingId(),
                event.dischargeUnLocode(), clock.instant());
        if (updated == 0) {
            log.info("キャンセルを書ける貨物の写しが無い（輸送開始前のキャンセル）: "
                    + "bookingId={}", event.bookingId());
        }
    }

    @EventHandler
    public void on(TrackingInitializedEvent event, @MessageIdentifier String eventId) {
        cargos.insert(new CargoSnapshotMapper.CargoSnapshotRow(
                event.trackingNumber(), event.bookingId(),
                event.originUnLocode(), event.destinationUnLocode(), event.cargoType(),
                // キャンセルはこのあと `CargoCancelledEvent` が書く（US30・IT15）。
                // **挿入では触らない**——追跡が作り直されても、キャンセルの印は
                // 消さない（上書きしない）。
                false,
                clock.instant(), eventId,
                // 陸揚げ地も同じ理由で触らない（挿入の SQL が列を書かない）。
                null));

        cargos.deleteLegs(event.trackingNumber());
        if (event.legs().isEmpty()) {
            return;
        }

        List<CargoSnapshotMapper.CargoSnapshotLegRow> legs = new ArrayList<>();
        for (int i = 0; i < event.legs().size(); i++) {
            var leg = event.legs().get(i);
            // **時刻は写さない**（ADR-0012 決定 4）。荷役が要るのは
            // 「どの航海がどの港で積み降ろすか」だけ。
            legs.add(new CargoSnapshotMapper.CargoSnapshotLegRow(event.trackingNumber(), i + 1,
                    leg.voyageNumber(), leg.loadUnLocode(), leg.unloadUnLocode()));
        }
        cargos.insertLegs(event.trackingNumber(), legs);
    }
}
