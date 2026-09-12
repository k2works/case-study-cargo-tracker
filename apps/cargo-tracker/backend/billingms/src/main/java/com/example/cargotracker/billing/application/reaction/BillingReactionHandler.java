package com.example.cargotracker.billing.application.reaction;

import com.example.cargotracker.billing.application.InvoiceCalculation;
import com.example.cargotracker.billing.infrastructure.projection.AttentionItemRecorder;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import java.time.Clock;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 引取 → 請求の反応（US21 §受入基準 1 / UC17）。
 *
 * <p><b>投影と別のパッケージに置く。</b> Processing Group はパッケージ名で分ける
 * （{@code @ProcessingGroup} は Axon 5 に無い）。同じにすると、投影のリプレイで
 * コマンドが再送され、請求書が二度作られる（[ADR-0001] 決定 6）。</p>
 *
 * <p><b>引渡のイベントだけでは料金を数えられない。</b> 区間・重量・貨物種別は
 * 自前の {@code billing_cargo_snapshot} から、荷主の種別と割引率は
 * {@code shipper_contract_snapshot} から読む。<b>同期問い合わせをしない</b>——
 * 相手が落ちている間は請求書が作れなくなる。</p>
 *
 * <p><b>作れないときは黙って進まない。</b> 材料が足りない（重量が無い・貨物の
 * 写しが来ていない・荷主の契約が来ていない）ときは請求書を作らず、<b>経理宛の
 * 要確認一覧に出す</b>（`architecture_backend.md`「配送完了後の請求書作成に失敗」）。
 * 足りない重量で安い請求を黙って出さない。</p>
 *
 * <p><b>二重に作らない。</b> 少なくとも 1 回配送なので同じ引渡が 2 度届きうる。
 * 有効な請求書が既にあれば送らない（不変条件 2 の一段目）。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "trackingNumber")
@Component
public class BillingReactionHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingReactionHandler.class);

    /** 作れなかった事実の宛先。<b>請求の失敗は経理</b>（要確認一覧の規約）。 */
    private static final String ACCOUNTANT = "ROLE_ACCOUNTANT";

    private final CommandGateway commands;
    private final InvoiceCalculation calculation;
    private final AttentionItemRecorder attentionItems;
    private final Clock clock;

    public BillingReactionHandler(CommandGateway commands, InvoiceCalculation calculation,
            AttentionItemRecorder attentionItems, Clock clock) {
        this.commands = commands;
        this.calculation = calculation;
        this.attentionItems = attentionItems;
        this.clock = clock;
    }

    @EventHandler
    public void on(CargoDeliveredEvent event) {
        switch (calculation.prepare(event.trackingNumber(), event.bookingId())) {
            case InvoiceCalculation.Outcome.AlreadyInvoiced ignored -> {
                // 同じ引取が 2 度届いた。送ると集約か投影のどちらかが弾くが、
                // **弾かれた事実が要確認に出る**ので、ここで止めるほうが静かである。
            }
            case InvoiceCalculation.Outcome.Blocked blocked -> {
                if (blocked.retryable()) {
                    // 購読の遅れ。**例外を投げて再試行させる**——退避先が受け止め、
                    // `projection:dead-letters:retry` で処理し直せる。
                    throw new IllegalStateException(blocked.reason());
                }
                // 待っても入らない。要確認に出して人に渡す。
                fail(event.bookingId(), blocked.reason());
            }
            // **default を置かない。** 結果の種類を足したとき、ここが名乗り出ずに
            // 素通りするのを防ぐ（コンパイルが赤になる）。
            case InvoiceCalculation.Outcome.Ready ready ->
                    commands.sendAndWait(ready.command(), String.class);
        }
    }

    /**
     * 作れなかった事実を経理へ渡す。
     *
     * <p><b>例外にしない。</b> 投げ直しても同じ結果にしかならない（材料が
     * 増えることはない）ので、退避先に積み続けるより人に渡すほうがよい。
     * <b>「例外にしない」は「記録しない」ではない</b>（IT7 の教訓）。</p>
     */
    private void fail(String bookingId, String reason) {
        log.warn("請求書を作れませんでした: {}", reason);
        attentionItems.add("REACTION_FAILED", "BOOKING", bookingId, ACCOUNTANT, reason, null,
                clock.instant());
    }
}
