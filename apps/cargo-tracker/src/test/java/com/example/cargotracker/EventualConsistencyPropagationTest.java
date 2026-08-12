package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ドメインイベントの購読側は<strong>新しいトランザクションで書く</strong>
 * （ADR-009 の規則 2）。
 *
 * <p>{@code AFTER_COMMIT} の時点で発行側のトランザクションは終わっている。
 * 購読側が既定の伝播（{@code REQUIRED}）で書くと、
 * <strong>書き込みがコミットされないまま静かに消える</strong>。
 *
 * <p><strong>テストでは気づけない。</strong> テストの経路では新しいトランザクションが
 * 始まって通ってしまうことがある。<strong>本番の構成でだけ消える</strong>のが
 * この誤りの性質である。
 *
 * <p>IT15 で実際に踏んだ。既存の購読先サービスは 5 本とも
 * {@code REQUIRES_NEW} を宣言していたのに、<strong>新しく足した 1 本だけが
 * 素の {@code @Transactional} だった</strong>。テストは全部緑だった。
 *
 * <p><strong>名簿ではなく実コードから導く。</strong> 購読ハンドラが注入している
 * サービスを辿り、そのサービスが伝播を宣言しているかを見る。
 * 名簿にすると、名簿に書き忘れたものが漏れる
 * （{@code MapperTableOwnershipTest} で実際に 3 イテレーション漏れた）。
 */
@DisplayName("イベント購読側は新しいトランザクションで書く（ADR-009）")
class EventualConsistencyPropagationTest {

    /**
     * 書き込みを伴わない協力者。
     *
     * <p>メトリクスや読み取り専用の問い合わせは伝播を宣言しない。
     * <strong>ここに足すときは「本当に書かないか」を確かめる。</strong>
     */
    private static final Set<String> WRITES_NOTHING = Set.of(
            "EventualConsistencySkips", "Clock", "Logger", "ApplicationEventPublisher");

    /** {@code private final 型 名前;} の型（コンストラクタ注入の協力者）。 */
    private static final Pattern INJECTED_FIELD = Pattern.compile(
            "private final ([A-Za-z][A-Za-z0-9_.]*(?:\\.[A-Z][A-Za-z0-9_]*)?)\\s+\\w+;");

    /**
     * <strong>購読ハンドラが呼ぶサービスは {@code REQUIRES_NEW} を宣言する。</strong>
     *
     * <p>違反があれば<strong>ハンドラ名とサービス名</strong>を並べて落とす。
     */
    @Test
    void 購読ハンドラが呼ぶサービスは新しいトランザクションで書く() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path handler : handlerFiles()) {
            String source = Files.readString(handler);
            for (String collaborator : injectedTypesIn(source)) {
                Path service = sourceOf(collaborator);
                if (service == null) {
                    continue;
                }
                String serviceSource = Files.readString(service);
                if (!serviceSource.contains("@Transactional")) {
                    // 書き込みを持たない協力者（問い合わせだけ）は対象外
                    continue;
                }
                if (!serviceSource.contains("Propagation.REQUIRES_NEW")) {
                    violations.add("%s が呼ぶ %s が REQUIRES_NEW を宣言していません"
                            .formatted(handler.getFileName(), collaborator));
                }
            }
        }

        assertThat(violations)
                .as("""
                        イベント購読側が既定の伝播で書いています（ADR-009 の規則 2）。
                        AFTER_COMMIT の時点で発行側のトランザクションは終わっており、
                        **書き込みがコミットされないまま静かに消えます。**
                        @Transactional(propagation = Propagation.REQUIRES_NEW) を宣言してください。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> ここでは実際のハンドラの形
     * （Javadoc つき・完全修飾名の混在・書き込みを伴わない協力者）をそのまま使う。
     */
    @Test
    void 実コードの形の注入を拾える() {
        String realShapedHandler = """
                @Component
                public class BillingCargoCancelledEventHandler {

                    private static final String SUBSCRIBER = "billing-cargo-cancelled";

                    private final ChargeCancellationFeeCommandService chargeService;
                    private final EventualConsistencySkips skips;

                    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
                    public void on(CargoCancelledEvent event) {
                    }
                }
                """;

        assertThat(injectedTypesIn(realShapedHandler))
                .as("書き込みを伴わない協力者は除き、サービスだけを拾えること（ADR-009 の規則 2）")
                .containsExactly("ChargeCancellationFeeCommandService");
    }

    /** 注入されている協力者の型名（単純名）。 */
    private static Set<String> injectedTypesIn(String source) {
        Set<String> types = new LinkedHashSet<>();
        Matcher matcher = INJECTED_FIELD.matcher(source);
        while (matcher.find()) {
            String type = matcher.group(1);
            String simple = type.substring(type.lastIndexOf('.') + 1);
            if (!WRITES_NOTHING.contains(simple)) {
                types.add(simple);
            }
        }
        return types;
    }

    /** 型名から実装のソースを探す（見つからなければ {@code null}）。 */
    private static Path sourceOf(String simpleName) {
        return SourceScan.main().filesEndingWith("/" + simpleName + ".java").stream()
                .findFirst()
                .orElse(SourceScan.main().files().stream()
                        .filter(p -> p.getFileName().toString().equals(simpleName + ".java"))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * 走査対象。<strong>空なら検査は何も見ていない</strong>。
     *
     * <p>走査の根が変わったときに<strong>静かに 0 件になり、緑のまま何も検査しない</strong>
     * 形を防ぐ（R8 で {@link SourceScan} へ寄せた際に歯止めを足した）。
     */
    private static List<Path> handlerFiles() {
        List<Path> found = handlerFilesScan();
        if (found.isEmpty()) {
            throw new AssertionError("イベントの購読 が 1 つも見つかりません。検査は何も見ていません");
        }
        return found;
    }

    private static List<Path> handlerFilesScan() {
        return SourceScan.main().files().stream()
                .filter(EventualConsistencyPropagationTest::subscribesToEvents)
                .toList();
    }

    private static boolean subscribesToEvents(Path path) {
        try {
            return Files.readString(path).contains("@TransactionalEventListener");
        } catch (IOException e) {
            return false;
        }
    }
}
