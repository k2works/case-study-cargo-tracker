package com.example.cargotracker.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.query.FindRouteCandidatesQuery;
import com.example.cargotracker.shared.contract.query.RouteCandidateDto;
import com.example.cargotracker.shared.contract.query.RouteCandidatesResponse;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 契約クエリの形をゴールデン JSON で固定する（テスト戦略「契約テスト」）。
 *
 * <p>クエリはイベントと違って永続化されないが、<b>両サービスが同じ形を期待している</b>
 * ことは同じである。片方だけフィールドを足すと、届いた側で読めないか、黙って
 * {@code null} になる。項目ごとの比較を積み上げず、丸ごと一致で比べる。</p>
 *
 * <p>実際に届くこと（往復）は {@code RouteCandidateContractIT} が 2 サービスを同じ
 * JVM に起こして確かめる。形の一致と、届くことは別の検査である。</p>
 */
class ContractQueryGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden-query");

    /** 名簿はパッケージの走査で導く。手書きにすると、足したものほど漏れる。 */
    private static List<String> contractQueryNames() {
        return new ClassFileImporter()
                .importPackages("com.example.cargotracker.shared.contract.query").stream()
                .filter(JavaClass::isRecord)
                // 入れ子（LegDto）は親の形の一部として一緒に固定される。
                .filter(type -> !type.getSimpleName().contains("$"))
                .filter(type -> type.getEnclosingClass().isEmpty())
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();
    }

    private final Converter converter = new JacksonConverter();

    static Stream<Object> contractQueries() {
        return Stream.of(
                new FindRouteCandidatesQuery("JPTYO", "USNYC", LocalDate.of(2026, 12, 1),
                        "HAZARDOUS", List.of("SGSIN"), "NLRTM"),
                // 応答の中身も単独で固定する。包んだ形だけを見ると、入れ子の
                // 形が変わったときに親の JSON の中でだけ壊れる。
                new RouteCandidateDto(
                        List.of(new RouteCandidateDto.LegDto("V-MOL-001", "JPTYO", "USNYC",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                        14, true),
                new RouteCandidatesResponse(List.of(new RouteCandidateDto(
                        List.of(new RouteCandidateDto.LegDto("V-MOL-001", "JPTYO", "SGSIN",
                                        Instant.parse("2026-09-10T09:00:00Z"),
                                        Instant.parse("2026-09-16T08:00:00Z")),
                                new RouteCandidateDto.LegDto("V-ONE-002", "SGSIN", "USNYC",
                                        Instant.parse("2026-09-17T06:00:00Z"),
                                        Instant.parse("2026-09-24T18:00:00Z"))),
                        15, false)), true));
    }

    private static String goldenOf(String name) {
        try {
            return Files.readString(GOLDEN_DIR.resolve(name + ".json"), StandardCharsets.UTF_8)
                    .strip();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "ゴールデン JSON がありません: " + name + ".json。契約クエリを足したら同時に置く", e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractQueries")
    @DisplayName("契約クエリの JSON が丸ごと一致する")
    void serializesExactlyAsGolden(Object message) {
        String name = message.getClass().getSimpleName();
        String actual =
                new String(converter.convert(message, byte[].class), StandardCharsets.UTF_8);

        assertThat(actual)
                .as("%s の形が変わった。片方の BC だけ直すと、届いた側で黙って null になる", name)
                .isEqualTo(goldenOf(name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractQueries")
    @DisplayName("ゴールデン JSON から元の型に戻せる（往復）")
    void deserializesFromGolden(Object message) {
        String name = message.getClass().getSimpleName();

        Object restored = converter.convert(
                goldenOf(name).getBytes(StandardCharsets.UTF_8), message.getClass());

        assertThat(restored).isEqualTo(message);
    }

    @Test
    @DisplayName("shared/contract/query の契約すべてにゴールデンがある")
    void everyContractQueryHasGolden() {
        List<String> names = contractQueryNames();
        assertThat(names)
                .as("契約クエリが 1 つも見つからないなら、検査は「揃っている」ではなく"
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
                    .containsExactlyInAnyOrderElementsOf(contractQueryNames());
        }
    }
}
