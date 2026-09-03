package com.example.cargotracker.shared.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** crypto-shredding の中核（ADR-0003）。鍵を捨てたら読めなくなることを固定する。 */
class ShipperDataCipherTest {

    @TempDir
    Path keyDirectory;

    private ShipperKeyRepository keys;
    private ShipperDataCipher cipher;

    @BeforeEach
    void setUp() {
        keys = new LocalFileShipperKeyRepository(keyDirectory);
        cipher = new ShipperDataCipher(keys);
    }

    @Test
    @DisplayName("暗号化して復号すると元に戻る")
    void roundTrips() {
        String envelope = cipher.encrypt("SHP-000001", "山田商事");

        assertThat(envelope).doesNotContain("山田商事");
        assertThat(cipher.decrypt("SHP-000001", envelope)).isEqualTo("山田商事");
    }

    @Test
    @DisplayName("エンベロープは鍵の参照名を持ち、平文を含まない")
    void envelopeHasExpectedShape() {
        String envelope = cipher.encrypt("SHP-000001", "yamada@example.com");

        assertThat(envelope)
                .contains("\"alg\":\"AES-256-GCM\"")
                .contains("\"keyRef\":\"alias/cargo-tracker/shipper/SHP-000001\"")
                .contains("\"iv\":\"")
                .contains("\"ciphertext\":\"")
                .doesNotContain("yamada@example.com");
    }

    @Test
    @DisplayName("同じ平文でも毎回違う暗号文になる（IV が固定でない）")
    void usesFreshIv() {
        String first = cipher.encrypt("SHP-000001", "山田商事");
        String second = cipher.encrypt("SHP-000001", "山田商事");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt("SHP-000001", first)).isEqualTo("山田商事");
        assertThat(cipher.decrypt("SHP-000001", second)).isEqualTo("山田商事");
    }

    @Test
    @DisplayName("鍵を破棄すると復号結果は null になる（例外にしない）")
    void returnsNullAfterKeyIsDestroyed() {
        String envelope = cipher.encrypt("SHP-000001", "山田商事");
        keys.destroy("SHP-000001");

        assertThat(cipher.decrypt("SHP-000001", envelope))
                .as("鍵破棄後のリプレイが止まると、削除要求に応えたことで業務全体が止まる")
                .isNull();
    }

    @Test
    @DisplayName("他の荷主の鍵は破棄の影響を受けない")
    void destroyingOneKeyDoesNotAffectOthers() {
        String mine = cipher.encrypt("SHP-000001", "山田商事");
        String other = cipher.encrypt("SHP-000002", "鈴木物流");

        keys.destroy("SHP-000001");

        assertThat(cipher.decrypt("SHP-000001", mine)).isNull();
        assertThat(cipher.decrypt("SHP-000002", other)).isEqualTo("鈴木物流");
    }

    @Test
    @DisplayName("null はそのまま null")
    void passesNullThrough() {
        assertThat(cipher.encrypt("SHP-000001", null)).isNull();
        assertThat(cipher.decrypt("SHP-000001", null)).isNull();
    }

    @Test
    @DisplayName("暗号化前に書かれた平文のイベントはそのまま読める")
    void passesNonEnvelopeThrough() {
        assertThat(cipher.decrypt("SHP-000001", "山田商事")).isEqualTo("山田商事");
    }

    @Test
    @DisplayName("鍵はあるのに開けないときは失敗させる（削除済みと区別する）")
    void failsWhenEnvelopeIsTampered() {
        String envelope = cipher.encrypt("SHP-000001", "山田商事");
        String tampered = envelope.replaceAll("\"ciphertext\":\"[^\"]+\"",
                "\"ciphertext\":\"AAAAAAAAAAAAAAAAAAAAAAAA\"");

        assertThatThrownBy(() -> cipher.decrypt("SHP-000001", tampered))
                .as("黙って null にすると「削除済み」と区別がつかなくなる")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("復号できませんでした");
    }
}
