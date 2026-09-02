package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.infrastructure.crypto.ShipperDataCipher;
import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 荷主の投影（Processing Group: booking-shipper-projection）。
 *
 * <p>Processing Group はこのクラスのパッケージ名で {@code application.yml} に書く。
 * {@code @ProcessingGroup} は Axon 5 に無い（ADR-0001 決定 3）。</p>
 *
 * <p><b>投影はコマンドを送らない。</b> 送るとリプレイのたびに副作用が再実行される。
 * 弾いた行は {@code attention_item} に残し、担当ロールの一覧（S70）に出す。</p>
 *
 * <p>メールアドレスの一意は三段の 2 段目と 3 段目をここで守る（domain-model.md）。
 * 1 段目（登録前の存在確認）は同時登録のレースで素通りするので、ここが最後の砦になる。</p>
 */
@Component
public class ShipperProjection {

    private static final Logger log = LoggerFactory.getLogger(ShipperProjection.class);

    private final ShipperMapper shippers;
    private final AttentionItemMapper attentionItems;
    private final ShipperDataCipher cipher;
    private final Clock clock;

    public ShipperProjection(ShipperMapper shippers, AttentionItemMapper attentionItems,
            ShipperDataCipher cipher, Clock clock) {
        this.shippers = shippers;
        this.attentionItems = attentionItems;
        this.cipher = cipher;
        this.clock = clock;
    }

    @EventHandler
    public void on(ShipperRegisteredEvent event) {
        String shipperId = event.shipperId();
        Instant now = clock.instant();

        // 鍵が破棄されていれば復号結果は null。列が NULL 許容なのはこのため（ADR-0003）。
        String name = cipher.decrypt(shipperId, event.name());
        String email = cipher.decrypt(shipperId, event.email());
        String phone = cipher.decrypt(shipperId, event.phone());
        String address = cipher.decrypt(shipperId, event.address());

        if (shippers.findById(shipperId) != null) {
            return; // リプレイで同じイベントを読み直しただけ。
        }

        try {
            shippers.insert(new ShipperMapper.ShipperRow(
                    shipperId,
                    shippers.nextShipperCode(),
                    event.shipperType(),
                    name,
                    email,
                    phone,
                    address,
                    "JP",
                    event.contractNumber(),
                    event.discountRate() == null ? null : new BigDecimal(event.discountRate()),
                    now,
                    now,
                    null));
        } catch (DuplicateKeyException e) {
            // 一意制約で弾かれた。集約は受け付けているので、ここで黙ると
            // 「登録したのに一覧に出ない」が誰にも見えないまま残る。
            recordAttention(shipperId, email, now);
        }
    }

    private void recordAttention(String shipperId, String email, Instant occurredAt) {
        log.warn("荷主の投影を一意制約で弾いた: shipperId={}", shipperId);
        attentionItems.insert(new AttentionItemMapper.AttentionItemRow(
                UUID.randomUUID().toString(),
                "PROJECTION_REJECTED",
                "SHIPPER",
                shipperId,
                "ROLE_SALES",
                "メールアドレスの重複",
                "{\"email\":" + jsonString(email) + "}",
                occurredAt,
                null,
                null));
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
