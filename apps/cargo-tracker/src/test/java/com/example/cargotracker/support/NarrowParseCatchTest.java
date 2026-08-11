package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>{@code catch} は解析だけを囲む</strong>（IT15 のふりかえり T2）。
 *
 * <p>ID の解析を「見つからない」に変える {@code catch} で<strong>読み出しまで囲むと</strong>、
 * 行の復元が投げた例外がそこに吸われる。画面には 404「見つかりません」だけが出て、
 * <strong>ログにも何も残らない</strong>。
 *
 * <p>IT15 で実際に踏んだ（P2）。{@code findCargo} で 9 本のテストが同じ 404 で落ち、
 * デバッグ出力を仕込む往復をして初めて本当の例外が見えた。
 * <strong>catch を狭めた瞬間に 1 分で直った。</strong>
 *
 * <p>IT16 で数え直したところ、<strong>同じ形が別のクラスに 2 件残っていた</strong>
 * （{@code MyBatisBookingQueryService} / {@code MyBatisShipperQueryService}）。
 * 片方は行の復元（{@code toView}）まで囲んでいた。
 * <strong>返済したことは、次に書くときに思い出す保証にならない。</strong>
 *
 * <p><strong>何を違反とするか。</strong> 次の 3 つが揃ったときだけ落とす。
 *
 * <ol>
 *   <li>{@code catch} が {@code IllegalArgumentException} を捕まえる</li>
 *   <li>ハンドラが「見つからない」に変換する（{@code Optional.empty()} / {@code NOT_FOUND}）</li>
 *   <li>{@code try} の中に読み出し（{@code mapper.} / {@code repository.}）がある</li>
 * </ol>
 *
 * <p>ドメインの生成が {@code IllegalArgumentException} を投げて「不正な入力」を返す形は
 * <strong>意図された意味であり、違反ではない</strong>。読み出しを含まないため、ここでは落ちない。
 */
@DisplayName("解析の catch に読み出しを含めない（IT15 の P2）")
class NarrowParseCatchTest {

    private static final Pattern TRY = Pattern.compile("\\btry\\s*\\{");
    private static final Pattern CATCH =
            Pattern.compile("\\s*catch\\s*\\(([^)]*?)\\s+\\w+\\)\\s*\\{(.*?)\\n\\s*\\}",
                    Pattern.DOTALL);
    private static final Pattern READ = Pattern.compile("\\b(mapper|repository)\\.\\w+\\(");

    /**
     * <strong>「見つからない」に変える catch が読み出しを囲んでいない。</strong>
     *
     * <p>違反があればファイルと行を並べて落とす。
     */
    @Test
    void 見つからないに変えるcatchは読み出しを囲まない() {
        List<String> violations = new ArrayList<>();
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            for (int line : wideParseCatchLines(source.code())) {
                violations.add("%s:%d".formatted(source.fileName(), line));
            }
        }

        assertThat(violations)
                .as("""
                        解析の catch が読み出しまで囲んでいます（IT15 の P2）。

                        行の復元や読み出しが投げた例外が「見つかりません」に化けて、
                        **ログにも何も残りません。**
                        解析だけを try に入れ、読み出しは catch の外に出してください。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。ここでは
     * IT16 で実際に直した {@code MyBatisBookingQueryService.findById} の前後をそのまま使う。
     */
    @Test
    void 実コードの形の違反と是正後を見分けられる() {
        String beforeFix = """
                @Override
                public Optional<BookingView> findById(String bookingId) {
                    try {
                        UUID id = UUID.fromString(bookingId);
                        // 詳細では確定した旅程も読む
                        return Optional.ofNullable(mapper.findByBookingId(id))
                                .map(row -> toView(row, mapper.findItinerary(id)));
                    } catch (IllegalArgumentException e) {
                        // UUID として解釈できない ID は「見つからない」として扱う
                        return Optional.empty();
                    }
                }
                """;

        String afterFix = """
                @Override
                public Optional<BookingView> findById(String bookingId) {
                    UUID id;
                    try {
                        id = UUID.fromString(bookingId);
                    } catch (IllegalArgumentException e) {
                        // UUID として解釈できない ID は「見つからない」として扱う
                        return Optional.empty();
                    }
                    return Optional.ofNullable(mapper.findByBookingId(id))
                            .map(row -> toView(row, mapper.findItinerary(id)));
                }
                """;

        assertThat(wideParseCatchLines(beforeFix))
                .as("読み出しを囲んだ catch を違反として拾えること")
                .isNotEmpty();
        assertThat(wideParseCatchLines(afterFix))
                .as("是正後を違反にしないこと（常に落ちる検査で緑にしない）")
                .isEmpty();
    }

    /**
     * <strong>意図された「不正な入力」の catch を巻き込まない。</strong>
     *
     * <p>ドメインの生成が {@code IllegalArgumentException} を投げ、それを業務上の
     * 「受け付けない」に変える形は<strong>設計どおりである</strong>。読み出しを
     * 含まないため落ちてはならない — <strong>常に落ちる検査で緑にしない。</strong>
     */
    @Test
    void ドメインの生成を囲むcatchは対象にしない() {
        String domainConstruction = """
                CancellationRequest request;
                try {
                    request = CancellationRequest.request(
                            id, reason, CancellationFeeRate.of(cargo.bookingStatus()),
                            actor, clock.instant());
                } catch (IllegalArgumentException e) {
                    return Result.rejected(e.getMessage());
                }
                """;

        assertThat(wideParseCatchLines(domainConstruction)).isEmpty();
    }

    /** 違反している {@code try} の開始行番号。 */
    private static List<Integer> wideParseCatchLines(String source) {
        List<Integer> lines = new ArrayList<>();
        Matcher tryMatcher = TRY.matcher(source);
        int from = 0;
        while (tryMatcher.find(from)) {
            int bodyStart = tryMatcher.end();
            int bodyEnd = closingBrace(source, bodyStart);
            from = bodyEnd;
            String body = source.substring(bodyStart, Math.max(bodyStart, bodyEnd - 1));
            Matcher catchMatcher = CATCH.matcher(source.substring(bodyEnd));
            if (!catchMatcher.lookingAt()) {
                continue;
            }
            if (!catchMatcher.group(1).contains("IllegalArgumentException")) {
                continue;
            }
            if (!convertsToNotFound(catchMatcher.group(2))) {
                continue;
            }
            if (READ.matcher(body).find()) {
                lines.add(countLines(source, bodyStart));
            }
        }
        return lines;
    }

    private static boolean convertsToNotFound(String handler) {
        return handler.contains("Optional.empty()") || handler.contains("NOT_FOUND");
    }

    private static int closingBrace(String source, int from) {
        int depth = 1;
        int i = from;
        while (i < source.length() && depth > 0) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            i++;
        }
        return i;
    }

    private static int countLines(String source, int offset) {
        return (int) source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }
}
