package com.example.cargotracker.booking.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.port.ShipperKeyRepository;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 暗号化がシリアライズ時に行われること（ADR-0003 決定 1）。
 *
 * <p>ここが Event Store に平文を入れない最後の砦になる。Controller で暗号化すると
 * 暗号文が値オブジェクトの検査に落ちるので、この位置でしか成立しない。</p>
 */
class ShipperDataEncryptingConverterTest {

    @TempDir
    Path keyDirectory;

    private ShipperKeyRepository keys;
    private Converter converter;

    @BeforeEach
    void setUp() {
        keys = new LocalFileShipperKeyRepository(keyDirectory);
        converter = new ShipperDataEncryptingConverter(new JacksonConverter(),
                new ShipperDataCipher(keys));
    }

    private static ShipperRegisteredEvent plaintextEvent() {
        return new ShipperRegisteredEvent("SHP-000001", "CORPORATE", "山田商事",
                "sales@example.com", "03-1111-1111", "東京都中央区", "CT-0001", "0.1000");
    }

    @Test
    @DisplayName("書き出したバイト列に平文が残らない")
    void writesNoPlaintext() {
        byte[] serialized = converter.convert(plaintextEvent(), byte[].class);
        String json = new String(serialized, StandardCharsets.UTF_8);

        assertThat(json)
                .doesNotContain("山田商事")
                .doesNotContain("sales@example.com")
                .doesNotContain("03-1111-1111")
                .doesNotContain("東京都中央区")
                .contains("AES-256-GCM");
        assertThat(json)
                .as("個人情報でない項目はそのまま。読めなくすると業務が回らない")
                .contains("CT-0001")
                .contains("SHP-000001");
    }

    @Test
    @DisplayName("読み戻すと平文に戻る")
    void roundTrips() {
        byte[] serialized = converter.convert(plaintextEvent(), byte[].class);

        ShipperRegisteredEvent restored =
                converter.convert(serialized, ShipperRegisteredEvent.class);

        assertThat(restored).isEqualTo(plaintextEvent());
    }

    @Test
    @DisplayName("鍵を破棄すると読み戻した個人情報が null になる（他の項目は残る）")
    void personalDataBecomesNullAfterShredding() {
        byte[] serialized = converter.convert(plaintextEvent(), byte[].class);

        keys.destroy("SHP-000001");

        ShipperRegisteredEvent restored =
                converter.convert(serialized, ShipperRegisteredEvent.class);

        assertThat(restored.name()).isNull();
        assertThat(restored.email()).isNull();
        assertThat(restored.phone()).isNull();
        assertThat(restored.address()).isNull();
        assertThat(restored.shipperType()).isEqualTo("CORPORATE");
        assertThat(restored.contractNumber()).isEqualTo("CT-0001");
    }

    @Test
    @DisplayName("荷主イベント以外はそのまま通す")
    void passesOtherPayloadsThrough() {
        byte[] serialized = converter.convert("ただの文字列", byte[].class);

        assertThat(converter.convert(serialized, String.class)).isEqualTo("ただの文字列");
    }
}
