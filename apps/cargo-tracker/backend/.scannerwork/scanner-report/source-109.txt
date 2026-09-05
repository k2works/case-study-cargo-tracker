package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 投影のリプレイ（[ADR-0001] コンプライアンス「投影がコマンドを送らない」）。
 *
 * <p>ArchUnit の {@code onlyInterfacesAndReactionSendCommands} はコンパイル時の依存
 * しか見ておらず、<b>実行時に呼ばれないことの保証ではない</b>。ここでは投影のハンドラを
 * もう一度流し、副作用が積み上がらないことを確かめる。</p>
 *
 * <p><b>投影の冪等性は「行が増えない」だけでは足りない。</b> 投影は
 * {@code attention_item}（追記専用の受け皿。リプレイで TRUNCATE しない）にも書く。
 * ここが増えると、要確認一覧が同じ内容で膨らみ、営業が毎朝見る一覧が信用されなくなる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplayIT extends AbstractAxonIntegrationTest {

    @Autowired
    private ShipperProjection shipperProjection;

    @Autowired
    private CargoProjection cargoProjection;

    @Autowired
    private ShipperMapper shippers;

    @Autowired
    private CargoSummaryMapper cargos;

    @Autowired
    private AttentionItemMapper attentionItems;

    private static ShipperRegisteredEvent shipper(String id, String email) {
        return new ShipperRegisteredEvent(id, "INDIVIDUAL", "山田商事", email,
                "03-0000-0000", "東京都中央区", null, null);
    }

    private static CargoBookedEvent cargo(String bookingId, String shipperId) {
        return new CargoBookedEvent(bookingId, shipperId, "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                10, "自動車部品", null, null, null, null, "sales01");
    }

    private int openAttentionCount() {
        return attentionItems.findOpenByRole("ROLE_SALES").size();
    }

    @Test
    @DisplayName("同じイベントを読み直しても荷主の行は増えない")
    void replayingShipperIsIdempotent() {
        String id = "SHP-REPLAY-" + System.nanoTime();
        String email = id + "@example.com";

        shipperProjection.on(shipper(id, email));
        String codeAfterFirst = shippers.findById(id).shipperCode();

        shipperProjection.on(shipper(id, email));

        assertThat(shippers.findById(id).shipperCode())
                .as("読み直しで荷主コードが振り直されると、書類と一覧が食い違う")
                .isEqualTo(codeAfterFirst);
    }

    @Test
    @DisplayName("同じイベントを読み直しても予約の行は増えない")
    void replayingCargoIsIdempotent() {
        String bookingId = "B-REPLAY-" + System.nanoTime();

        cargoProjection.on(cargo(bookingId, "SHP-X"));
        String numberAfterFirst = cargos.findById(bookingId).bookingNumber();

        cargoProjection.on(cargo(bookingId, "SHP-X"));

        assertThat(cargos.findById(bookingId).bookingNumber()).isEqualTo(numberAfterFirst);
    }

    @Test
    @DisplayName("弾かれた登録を読み直しても要確認一覧は増えない")
    void replayingRejectedShipperDoesNotDuplicateAttentionItems() {
        // attention_item は追記専用でリプレイでも消さない（data-model.md）。
        // 投影が毎回書くと、同じ内容の行が読み直しの回数だけ積み上がる。
        String email = "replay-dup-" + System.nanoTime() + "@example.com";
        shipperProjection.on(shipper("SHP-FIRST-" + System.nanoTime(), email));

        String rejectedId = "SHP-REJECTED-" + System.nanoTime();
        shipperProjection.on(shipper(rejectedId, email));
        int afterFirstRejection = openAttentionCount();

        shipperProjection.on(shipper(rejectedId, email));

        assertThat(openAttentionCount())
                .as("読み直しのたびに増えると、要確認一覧が同じ内容で膨らんで信用されなくなる")
                .isEqualTo(afterFirstRejection);
    }
}
