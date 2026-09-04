package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.Email;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * `Shipper` がイベント列から復元できること。
 *
 * <p>`Cargo` と同じく、{@code @EventTag} が無いと集約は毎回空のまま復元される
 * （[ADR-0001] 決定 5 第 8 項）。`Shipper` は IT1 の時点で登録しかせず、復元した
 * 状態を見る判断が 1 つも無かったため、この欠落が 2 IT のあいだ気づかれなかった。</p>
 *
 * <p>復元を見る守り（契約変更・連絡先更新・削除済みの判定）を足す前に、ここで
 * 復元そのものを固定する。<b>守りを足してから気づくと、その守りが働いていない
 * ことに気づけない。</b></p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperRestoreIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CommandGateway commandGateway;

    private static RegisterShipperCommand register(String shipperId, String email) {
        return new RegisterShipperCommand(shipperId, "山田商事", ShipperType.INDIVIDUAL,
                new Email(email), "03-0000-0000", "東京都中央区", null);
    }

    @Test
    @DisplayName("同じ荷主 ID で 2 度は登録できない")
    void rejectsDoubleRegistration() {
        // 復元した集約が既に登録を持っているかを見る。@EventTag が無いと集約は
        // 空のまま復元され、この守りは何度でも素通りする。
        String shipperId = "SHP-RESTORE-" + System.nanoTime();
        String email = shipperId + "@example.com";

        assertThat(commandGateway.sendAndWait(register(shipperId, email), String.class))
                .isEqualTo(shipperId);

        // 型ではなく文言で見る。サービス越しに来た例外は根の型が置き換わる
        // （ADR-0001 決定 5 第 12 項）。
        assertThat(catchThrowableMessage(() ->
                commandGateway.sendAndWait(register(shipperId, email), String.class)))
                .contains("既に登録されています");
    }

    private static String catchThrowableMessage(Runnable action) {
        try {
            action.run();
            return "";
        } catch (RuntimeException e) {
            return e.getMessage() == null ? "" : e.getMessage();
        }
    }
}
