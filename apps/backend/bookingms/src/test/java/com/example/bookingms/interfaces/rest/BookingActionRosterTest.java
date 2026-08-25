package com.example.bookingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 予約に対して行える操作の名簿が、<strong>サーバ・画面・モックで一致している</strong>ことを
 * 検査する（[ADR-021]）。
 *
 * <p><strong>載せ忘れは画面から見えない形で効く。</strong>集約に述語があっても応答に
 * 載らなければ、画面はボタンを出さない。モックだけが返していると、モックのテストは
 * 全部緑のまま——IT9 の {@code REQUEST_CANCELLATION} が実際にその状態で、
 * <strong>実環境では営業がキャンセルを申請できなかった</strong>。API を直接叩く
 * 受け入れテストでも見つからない（画面を通らないため）。
 *
 * <p>名簿は書き写さない。3 か所それぞれの実体から読み取って突き合わせる。
 */
@DisplayName("予約の操作の名簿")
class BookingActionRosterTest {

    private static final Path REPO_ROOT = Path.of("../../..").toAbsolutePath().normalize();

    private static final Path FRONTEND_TYPES =
            REPO_ROOT.resolve("apps/frontend/src/features/booking/types.ts");

    private static final Path MOCK_DATA = REPO_ROOT.resolve("apps/frontend/src/mocks/data.ts");

    @Test
    @DisplayName("集約が「できる」と言う操作は、すべて応答に載る")
    void everyPredicateIsCarriedInTheResponse() {
        String response = read(REPO_ROOT.resolve("apps/backend/bookingms/src/main/java/"
                + "com/example/bookingms/interfaces/rest/BookingResponse.java"));

        List<String> declared = Arrays.stream(BookingAction.values()).map(Enum::name).toList();

        assertThat(declared)
                .as("操作が 1 つも読めていない。検査が何も守らないまま緑になる")
                .isNotEmpty();
        assertThat(declared)
                .allSatisfy(action -> assertThat(response)
                        .as("%s が応答の組み立てに現れない。画面はこの操作のボタンを出せない",
                                action)
                        .contains("BookingAction." + action));
    }

    /**
     * 画面の型と一致する。
     *
     * <p>画面にしか無い操作は、<strong>モックだけが返している</strong>合図である。
     */
    @Test
    @DisplayName("画面が知っている操作と、サーバが返す操作が一致する")
    void theScreenKnowsExactlyWhatTheServerSends() {
        // **その型の宣言だけを読む。** ファイル全体から拾うと、貨物種別のような
        // 別の合併型まで混ざり、検査が意味を失う
        assertThat(namesIn(bookingActionDeclaration(), "\\|\\s*'([A-Z_]+)'"))
                .as("画面とサーバで操作の名簿が食い違っている。"
                        + "画面にしか無いものは、モックだけが返している")
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(BookingAction.values()).map(Enum::name).toList());
    }

    /** モックが返す操作も、サーバが返しうるものだけである。 */
    @Test
    @DisplayName("モックが返す操作は、すべてサーバも返しうる")
    void theMockDoesNotInventActions() {
        Set<String> declared = new LinkedHashSet<>(
                Arrays.stream(BookingAction.values()).map(Enum::name).toList());

        assertThat(namesIn(read(MOCK_DATA), "actions\\.push\\('([A-Z_]+)'\\)"))
                .as("モックがサーバの知らない操作を返している。"
                        + "モックの上では動く画面が、実環境では動かない")
                .isNotEmpty()
                .allSatisfy(action -> assertThat(declared).contains(action));
    }

    /** 画面の {@code BookingAction} 型の宣言部分だけを切り出す。 */
    private static String bookingActionDeclaration() {
        String source = read(FRONTEND_TYPES);
        int start = source.indexOf("export type BookingAction =");
        assertThat(start).as("画面の BookingAction 型が見つからない").isNotNegative();
        int end = source.indexOf("\n\n", start);
        return source.substring(start, end < 0 ? source.length() : end);
    }

    private static Set<String> namesIn(String source, String pattern) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile(pattern).matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
