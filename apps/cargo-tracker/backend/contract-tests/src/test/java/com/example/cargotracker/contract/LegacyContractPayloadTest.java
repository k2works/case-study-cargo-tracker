package com.example.cargotracker.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
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
 * 一度 Event Store に入った形は、いまのレコードでも読めなければならない（IT11 引き継ぎ H.6）。
 *
 * <p><b>ゴールデンは「いまの形」しか見ていない。</b> 契約イベントに項目を足すと
 * ゴールデンも一緒に書き換わるので、<b>足す前の形が読めなくなっていても緑のまま</b>に
 * なる。イベントは追記専用で、過去のイベントは書き換えられない——読めなくなった
 * 時点で、その貨物は復元できない。</p>
 *
 * <p>置き方は {@code golden-legacy/<イベント名>#<いつ・何が違うか>.json}。
 * 項目を足したら、<b>足す前の形をここへ置く</b>。</p>
 */
class LegacyContractPayloadTest {

    private static final Path LEGACY_DIR = Path.of("src/test/resources/golden-legacy");
    private static final String EVENT_PACKAGE = "com.example.cargotracker.shared.contract.event.";

    private final Converter converter = new JacksonConverter();

    static Stream<Path> legacyPayloads() throws IOException {
        try (Stream<Path> files = Files.list(LEGACY_DIR)) {
            return files.sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("legacyPayloads")
    @DisplayName("昔の形のイベントが、いまのレコードへ読み戻せる")
    void readsOldPayloadIntoTheCurrentRecord(Path payload) throws Exception {
        Class<?> type = eventTypeOf(payload);
        byte[] json = Files.readString(payload, StandardCharsets.UTF_8).strip()
                .getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> converter.convert(json, type))
                .as("%s が読めない。項目を足すときは、足す前の形も読めるようにする"
                        + "（既定値を持つか、Upcaster を足す）", payload.getFileName())
                .doesNotThrowAnyException();

        Object restored = converter.convert(json, type);
        assertThat(restored)
                .as("読めたが中身が空では、復元できたことにならない")
                .isNotNull();
        assertThat(restored.toString())
                .as("**足りない項目だけが空になる。** 他の項目まで落ちていたら、"
                        + "読めているように見えて中身が失われている")
                .contains("TRK-");
    }

    @Test
    @DisplayName("置き場が空なら、この検査は「守っている」ではなく「調べていない」")
    void hasPayloadsToCheck() throws IOException {
        assertThat(legacyPayloads().toList())
                .as("契約の形を変えたことがあるなら、変える前の形がここにあるはず")
                .isNotEmpty();
    }

    @Test
    @DisplayName("ファイル名は実在する契約イベントを指している")
    void namesAnExistingContractEvent() throws IOException {
        List<Path> payloads = legacyPayloads().toList();
        for (Path payload : payloads) {
            assertThatCode(() -> eventTypeOf(payload))
                    .as("%s に対応する契約イベントが無い。改名したなら、"
                            + "この置き場も同じ変更で直す", payload.getFileName())
                    .doesNotThrowAnyException();
        }
    }

    /** {@code <イベント名>#<説明>.json} からイベントの型を引く。 */
    private static Class<?> eventTypeOf(Path payload) throws ClassNotFoundException {
        String fileName = payload.getFileName().toString();
        String simpleName = fileName.substring(0, fileName.indexOf('#'));
        return Class.forName(EVENT_PACKAGE + simpleName);
    }
}
