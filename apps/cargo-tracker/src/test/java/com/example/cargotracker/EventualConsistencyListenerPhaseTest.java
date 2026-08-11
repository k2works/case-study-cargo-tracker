package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BC をまたぐドメインイベントの購読は <strong>{@code AFTER_COMMIT} である</strong>
 * （ADR-009 の規則 1）。
 *
 * <p>コミット前に動くと、<strong>発行側が巻き戻ったときに購読側だけが残る</strong>。
 * 荷役の登録が失敗して消えたのに、追跡の輸送状態だけが進む — という形になる。
 *
 * <p><strong>なぜ {@link EventualConsistencyPropagationTest} では足りないか。</strong>
 * あちらは購読ハンドラを「{@code @TransactionalEventListener} を含むファイル」として
 * 探している。つまり <strong>素の {@code @EventListener} に書き換えると、
 * そのファイルは検査の対象から消えて緑になる</strong>。規則 2（新しいトランザクション）
 * を守る検査が、規則 1 を破った瞬間に目を閉じる構造だった。
 *
 * <p>IT16 で ADR に守り手を書き出したときに判明した。ADR-009 は「必ず守ること」を
 * 5 件挙げているが、<strong>検査があったのは 2 件だけだった</strong>。
 *
 * <p>本検査は<strong>イベントの側から探す</strong>。{@code shared/domain/event} の型を
 * 引数に取る購読メソッドを集め、それが {@code AFTER_COMMIT} を宣言しているかを見る。
 * 探し方をアノテーションに依存させない — <strong>アノテーションこそが検査対象だからである</strong>。
 */
@DisplayName("BC をまたぐイベントの購読は AFTER_COMMIT である（ADR-009 の規則 1）")
class EventualConsistencyListenerPhaseTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path SHARED_EVENTS =
            MAIN.resolve("com/example/cargotracker/shared/domain/event");

    /**
     * <strong>BC をまたぐイベントを購読するなら {@code AFTER_COMMIT} を宣言する。</strong>
     *
     * <p>違反があればファイル名を並べて落とす。
     */
    @Test
    void 共有イベントを購読するクラスはAFTER_COMMITを宣言する() throws IOException {
        Set<String> events = sharedEventTypes();
        assertThat(events)
                .as("共有イベントが 1 つも見つからないなら、検査は何も見ていない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path source : javaFilesUnder(MAIN)) {
            if (source.startsWith(SHARED_EVENTS)) {
                continue;
            }
            String text = Files.readString(source);
            if (!subscribesToAnyOf(text, events)) {
                continue;
            }
            if (!declaresAfterCommit(text)) {
                violations.add(source.getFileName().toString());
            }
        }

        assertThat(violations)
                .as("""
                        BC をまたぐイベントの購読が AFTER_COMMIT を宣言していません
                        （ADR-009 の規則 1）。

                        コミット前に動くと、発行側が巻き戻ったときに購読側だけが残ります。
                        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
                        を宣言してください。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。ここでは実際の
     * ハンドラの形（Javadoc つき・定数つき・注入フィールドつき）をそのまま使う。
     */
    @Test
    void 素のEventListenerに戻した形を違反として拾える() {
        Set<String> events = Set.of("CargoCancelledEvent", "HandlingActivityRegisteredEvent");

        String violatingShape = """
                /** キャンセルの確定を購読して、キャンセル料の請求書を作る。 */
                @Component
                public class BillingCargoCancelledEventHandler {

                    private static final String SUBSCRIBER = "billing-cargo-cancelled";

                    private final ChargeCancellationFeeCommandService chargeService;

                    @EventListener
                    public void on(CargoCancelledEvent event) {
                        chargeService.charge(event.bookingId());
                    }
                }
                """;

        String validShape = """
                @Component
                public class TrackingHandlingEventHandler {

                    private final TrackingCommandService trackingService;

                    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
                    public void on(HandlingActivityRegisteredEvent event) {
                        trackingService.apply(event);
                    }
                }
                """;

        assertThat(subscribesToAnyOf(violatingShape, events)).isTrue();
        assertThat(declaresAfterCommit(violatingShape))
                .as("素の @EventListener は違反として拾えること")
                .isFalse();

        assertThat(subscribesToAnyOf(validShape, events)).isTrue();
        assertThat(declaresAfterCommit(validShape))
                .as("正しい形を違反にしないこと（常に落ちる検査で緑にしない）")
                .isTrue();
    }

    /**
     * <strong>共有イベントを購読していないクラスを巻き込まない。</strong>
     *
     * <p>認証イベントの購読（{@code AuthenticationAuditListener}）は BC をまたぐ
     * ドメインイベントではなく、Spring Security が発行する枠組みのイベントである。
     * <strong>結果整合の対象ではないため、AFTER_COMMIT を強いない。</strong>
     */
    @Test
    void 共有イベント以外の購読は対象にしない() {
        Set<String> events = Set.of("CargoCancelledEvent");

        String authenticationEventSubscriber = """
                @Component
                public class AuthenticationAuditListener {

                    @EventListener
                    public void on(AuthenticationFailureBadCredentialsEvent event) {
                    }
                }
                """;

        assertThat(subscribesToAnyOf(authenticationEventSubscriber, events)).isFalse();
    }

    /** {@code shared/domain/event} に置かれたイベント型の単純名。 */
    private static Set<String> sharedEventTypes() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (Path source : javaFilesUnder(SHARED_EVENTS)) {
            String fileName = source.getFileName().toString();
            String simple = fileName.substring(0, fileName.length() - ".java".length());
            if (simple.endsWith("Event")) {
                names.add(simple);
            }
        }
        return names;
    }

    /** イベントを引数に取る購読メソッドを持つか。 */
    private static boolean subscribesToAnyOf(String source, Set<String> events) {
        if (!source.contains("EventListener")) {
            return false;
        }
        return events.stream().anyMatch(event -> source.contains("(" + event + " "));
    }

    private static boolean declaresAfterCommit(String source) {
        return source.contains("@TransactionalEventListener")
                && source.contains("AFTER_COMMIT");
    }

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
