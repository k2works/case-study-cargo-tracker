package com.example.cargotracker.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 契約イベントの形をゴールデン JSON で固定する。
 *
 * <p>イベントは集約の<b>永続化フォーマット</b>なので、フィールドを消す・型を変えると
 * 過去のイベントが読めなくなる。項目ごとの比較を積み上げると、属性が増えたときに
 * 増えた分の検査が抜ける。ここでは<b>丸ごと一致</b>で比べる。</p>
 *
 * <p>形を変えるときは、この JSON を書き換えるのではなく Upcaster を足す。
 * 書き換えてよいのは「まだ本番に出ていないイベント」だけ。</p>
 */
class ContractEventGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");

    /** 契約イベントの名簿。ADR-0001 決定 4 の「契約イベント 11 本」の正典と対にする。 */
    private static final List<String> CONTRACT_EVENT_NAMES = List.of(
            "ShipperRegisteredEvent");

    private final Converter converter = new JacksonConverter();

    static Stream<Object> contractEvents() {
        return Stream.of(
                new ShipperRegisteredEvent("SHP-000001", "CORPORATE", "山田商事",
                        "sales@example.com", "03-1111-1111", "東京都中央区", "CT-0001", "0.1000"));
    }

    private static String goldenOf(String name) {
        try {
            return Files.readString(GOLDEN_DIR.resolve(name + ".json"), StandardCharsets.UTF_8)
                    .strip();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "ゴールデン JSON がありません: " + name + ".json。契約イベントを足したら同時に置く", e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractEvents")
    @DisplayName("契約イベントの JSON が丸ごと一致する")
    void serializesExactlyAsGolden(Object event) {
        String name = event.getClass().getSimpleName();
        String actual = new String(converter.convert(event, byte[].class), StandardCharsets.UTF_8);

        assertThat(actual)
                .as("%s の形が変わった。過去のイベントが読めなくなるので、"
                        + "ゴールデンを書き換えるのではなく Upcaster を足す", name)
                .isEqualTo(goldenOf(name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractEvents")
    @DisplayName("ゴールデン JSON から元の型に戻せる")
    void deserializesFromGolden(Object event) {
        String name = event.getClass().getSimpleName();

        Object restored = converter.convert(
                goldenOf(name).getBytes(StandardCharsets.UTF_8), event.getClass());

        assertThat(restored).isEqualTo(event);
    }

    @Test
    @DisplayName("名簿の契約イベントすべてにゴールデンがある")
    void everyContractEventHasGolden() {
        for (String name : CONTRACT_EVENT_NAMES) {
            assertThat(GOLDEN_DIR.resolve(name + ".json"))
                    .as("%s のゴールデンが無い。契約を足したら同じ変更でここにも置く", name)
                    .exists();
        }
    }

    @Test
    @DisplayName("検査していないゴールデンが残っていない")
    void hasNoOrphanGolden() throws IOException {
        try (Stream<Path> files = Files.list(GOLDEN_DIR)) {
            List<String> onDisk = files
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .toList();

            assertThat(onDisk)
                    .as("契約を消したらゴールデンも消す。残しておくと、検査していない形が"
                            + "「固定されている」ように見える")
                    .containsExactlyInAnyOrderElementsOf(CONTRACT_EVENT_NAMES);
        }
    }
}
