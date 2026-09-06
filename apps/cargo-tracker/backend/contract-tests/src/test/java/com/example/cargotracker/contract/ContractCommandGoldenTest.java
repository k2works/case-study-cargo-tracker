package com.example.cargotracker.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 契約コマンドの形をゴールデン JSON で固定する（テスト戦略「契約テスト」）。
 *
 * <p><b>コマンドはクエリより壊れ方が悪い。</b> クエリは応答が読めなければその場で
 * 分かるが、コマンドは受け取った側が黙って {@code null} のまま処理を進められる。
 * 項目ごとの比較を積み上げず、丸ごと一致で比べる。</p>
 *
 * <p>実際に届くこと（両サービスを起こした往復）は別の検査である。</p>
 */
class ContractCommandGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden-command");

    /** 名簿はパッケージの走査で導く。手書きにすると、足したものほど漏れる。 */
    private static List<String> contractCommandNames() {
        return new ClassFileImporter()
                .importPackages("com.example.cargotracker.shared.contract.command").stream()
                .filter(JavaClass::isRecord)
                // 入れ子（LegDto）は親の形の一部として一緒に固定される。
                .filter(type -> type.getEnclosingClass().isEmpty())
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();
    }

    private final Converter converter = new JacksonConverter();

    static Stream<Object> contractCommands() {
        return Stream.of(
                new InitializeTrackingCommand("T-2026-0001", "b-1", "JPTYO", "USNYC", "GENERAL",
                        List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO",
                                        "SGSIN", Instant.parse("2026-09-10T09:00:00Z"),
                                        Instant.parse("2026-09-16T08:00:00Z")),
                                new InitializeTrackingCommand.LegDto("V-ONE-002", "SGSIN",
                                        "USNYC", Instant.parse("2026-09-17T06:00:00Z"),
                                        Instant.parse("2026-09-24T18:00:00Z"))),
                        Instant.parse("2026-09-08T01:00:00Z")));
    }

    private static String goldenOf(String name) {
        try {
            return Files.readString(GOLDEN_DIR.resolve(name + ".json"), StandardCharsets.UTF_8)
                    .strip();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "ゴールデン JSON がありません: " + name + ".json。契約コマンドを足したら同時に置く", e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCommands")
    @DisplayName("契約コマンドの JSON が丸ごと一致する")
    void serializesExactlyAsGolden(Object message) {
        String name = message.getClass().getSimpleName();
        String actual =
                new String(converter.convert(message, byte[].class), StandardCharsets.UTF_8);

        assertThat(actual)
                .as("%s の形が変わった。受け取った側は黙って null のまま処理を進める", name)
                .isEqualTo(goldenOf(name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCommands")
    @DisplayName("ゴールデン JSON から元の型に戻せる（往復）")
    void deserializesFromGolden(Object message) {
        String name = message.getClass().getSimpleName();

        Object restored = converter.convert(
                goldenOf(name).getBytes(StandardCharsets.UTF_8), message.getClass());

        assertThat(restored).isEqualTo(message);
    }

    @Test
    @DisplayName("shared/contract/command の契約すべてにゴールデンがある")
    void everyContractCommandHasGolden() {
        List<String> names = contractCommandNames();
        assertThat(names)
                .as("契約コマンドが 1 つも見つからないなら、検査は「揃っている」ではなく"
                        + "「調べていない」で緑になる")
                .isNotEmpty();

        for (String name : names) {
            assertThat(GOLDEN_DIR.resolve(name + ".json"))
                    .as("%s のゴールデンが無い。契約を足したら同じ変更でここにも置く", name)
                    .exists();
        }
    }

    @Test
    @DisplayName("検査していないゴールデンが残っていない")
    void hasNoOrphanGolden() throws IOException {
        try (Stream<Path> files = Files.list(GOLDEN_DIR)) {
            assertThat(files.map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted().toList())
                    .as("契約を消したらゴールデンも消す")
                    .containsExactlyInAnyOrderElementsOf(contractCommandNames());
        }
    }
}
