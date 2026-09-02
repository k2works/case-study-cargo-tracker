package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.port.ShipperKeyRepository;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import com.example.cargotracker.booking.infrastructure.projection.ShipperProjection;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.axonframework.conversion.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * デモ項目 6：鍵を破棄 → 投影をリプレイ → 個人情報が「（削除済み）」になる。
 *
 * <p>リプレイは、投影テーブルの行を消して同じイベントを流し直すことで表す。
 * Converter がイベントを復号するので、鍵が無ければ個人情報は null で届く。</p>
 */
public class ShipperShreddingSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ShipperKeyRepository keys;

    @Autowired
    private ShipperProjection projection;

    @Autowired
    private ShipperMapper shippers;

    @Autowired
    private Converter converter;

    @Autowired
    private DataSource dataSource;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String shipperId;
    private byte[] storedEvent;

    @前提("メールアドレス {string} の荷主 {string} を登録し投影に出るまで待つ")
    public void 荷主が登録されている(String email, String name) {
        ResponseEntity<JsonMap> created = rest.post()
                .uri("http://localhost:" + port + "/api/v1/booking/shippers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "shipperType", "CORPORATE", "email", email,
                        "phone", "03-0000-0000", "address", "東京都中央区",
                        "contractNumber", "CT-0001", "discountRate", "0.1000"))
                .retrieve().toEntity(JsonMap.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        shipperId = String.valueOf(created.getBody().get("shipperId"));

        SharedSteps.awaitWithin(10, () -> shippers.findById(shipperId) != null,
                "登録した荷主が投影に出る");

        // Event Store に入っているのと同じ形（暗号文）を控える。
        storedEvent = converter.convert(new ShipperRegisteredEvent(shipperId, "CORPORATE",
                name, email, "03-0000-0000", "東京都中央区", "CT-0001", "0.1000"), byte[].class);
        assertThat(new String(storedEvent, java.nio.charset.StandardCharsets.UTF_8))
                .as("Event Store には暗号文だけが入る")
                .doesNotContain(name)
                .doesNotContain(email);
    }

    @もし("その荷主の鍵を破棄する")
    public void 鍵を破棄する() {
        keys.destroy(shipperId);
    }

    @かつ("投影をリプレイする")
    public void リプレイする() {
        // リプレイ = 投影テーブルを空にして、同じイベントを流し直すこと。
        new JdbcTemplate(dataSource).update("DELETE FROM shipper WHERE shipper_id = ?", shipperId);
        projection.on(converter.convert(storedEvent, ShipperRegisteredEvent.class));
    }

    @ならば("その荷主の氏名は読めない")
    public void 氏名は読めない() {
        assertThat(shippers.findById(shipperId).name()).isNull();
    }

    @かつ("その荷主のメールアドレスは読めない")
    public void メールは読めない() {
        assertThat(shippers.findById(shipperId).email()).isNull();
    }

    @かつ("その荷主の荷主コードと種別は残っている")
    public void 業務項目は残る() {
        ShipperMapper.ShipperRow row = shippers.findById(shipperId);
        assertThat(row.shipperCode()).matches("SHP-\\d{6}");
        assertThat(row.shipperType()).isEqualTo("CORPORATE");
    }
}
