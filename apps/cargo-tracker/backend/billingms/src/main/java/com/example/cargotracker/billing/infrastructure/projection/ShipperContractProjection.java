package com.example.cargotracker.billing.infrastructure.projection;

import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import com.example.cargotracker.billing.infrastructure.persistence.ShipperContractSnapshotMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.math.BigDecimal;
import java.time.Clock;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 荷主の契約スナップショット（Processing Group: billing-projection）。
 *
 * <p>US03 §受入基準 4「登録した法人情報は US22（法人割引を適用する）で参照される」の実体。
 * billingms は請求のたびに bookingms へ問い合わせず、契約イベントを購読して自分の
 * 読み取りモデルに写す（ACL。`data-model.md`「billing_read_db」）。同期問い合わせに
 * すると、bookingms が落ちている間は請求書が作れなくなる。</p>
 *
 * <p><b>投影はコマンドを送らない。</b> 送るとリプレイのたびに副作用が再実行される。</p>
 *
 * <p><b>処理の列を荷主ごとに分ける。</b> 既定では列が全体で 1 本なので、1 件の毒で
 * <b>無関係の荷主のイベントまで退避される</b>（IT12 のクラスタ E2E で 4 件のうち 3 件が
 * 巻き添え）。退避先は順序を守るために「同じ列の後続」も退避するので、列の切り方が
 * そのまま被害の範囲になる。同じ荷主の中では順序が要る（訂正は登録より後に効かなければ
 * ならない）ので、荷主より細かくは切らない。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "shipperId")
@Component
public class ShipperContractProjection {

    private final ShipperContractSnapshotMapper snapshots;
    private final Clock clock;

    public ShipperContractProjection(ShipperContractSnapshotMapper snapshots, Clock clock) {
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @EventHandler
    public void on(ShipperRegisteredEvent event) {
        // 氏名は暗号化されて届き、CryptoConfiguration の Converter が復号する。
        // 鍵を破棄した荷主では null になる（ADR-0003）。列が NULL 許容なのはこのため。
        snapshots.upsert(new ShipperContractSnapshotMapper.SnapshotRow(
                event.shipperId(),
                event.name(),
                event.shipperType(),
                event.discountRate() == null ? null : new BigDecimal(event.discountRate()),
                event.contractNumber(),
                clock.instant(),
                null));
    }
}
