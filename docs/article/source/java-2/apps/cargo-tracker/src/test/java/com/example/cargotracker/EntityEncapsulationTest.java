package com.example.cargotracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <strong>{@code entities} の生成・変更メソッドを、呼んでよい相手だけが呼んでいること</strong>
 * （ADR-024 の代償を返す検査。IT20 / D5）。
 *
 * <p>ADR-024 で {@code domain/model} を構成要素ごとのサブパッケージへ分けた結果、
 * <strong>それまで javac が止めていた越境が止まらなくなった</strong>。分割前は
 * 集約ルートと同じパッケージにいることでパッケージプライベートが境界になっていたが、
 * サブパッケージへ移すと {@code public} にせざるを得ない。ADR-024 は
 * 「この損失を検査で埋め合わせていない」「実測の違反は 0 件」と書き、
 * D5 として次のイテレーションへ送った。<strong>本テストがその埋め合わせである。</strong>
 *
 * <h2>契約は ADR-024 の表そのものである</h2>
 *
 * <table>
 *   <caption>公開せざるを得なかった 4 メソッドと、呼んでよい相手</caption>
 *   <tr><th>型</th><th>メソッド</th><th>呼んでよい相手</th></tr>
 *   <tr><td>{@code ProposedRoute}</td><td>{@code of} / {@code withPriority}</td>
 *       <td>{@code RouteSearchService}（ドメインサービス）</td></tr>
 *   <tr><td>{@code TrackingExceptionEvent}</td><td>{@code raise} / {@code resolve}</td>
 *       <td>{@code TrackingActivity}（集約ルート）</td></tr>
 * </table>
 *
 * <p><strong>「集約ルート以外は呼べない」と一般化して書かない。</strong> そう書くと
 * {@code ProposedRoute.of} を集約ルートへ移すことになり、探索と提案の分離が壊れる
 * （{@code of} の 6 引数はすべて探索の途中でしか作れない）。ADR-024 が定めているのは
 * <strong>メソッドごとに相手を書き分けた表</strong>であり、規則を一般化することは
 * 規則の書き換えである。
 *
 * <p><strong>型名だけで判定しない。</strong> {@code raise} や {@code resolve} は
 * 他の型にも存在する（{@code ExceptionOccurrence.raise} は値オブジェクト、
 * {@code TrackingExceptionCommandService.resolve} はコマンドサービス）。
 * 判定は<strong>（呼び先の型・メソッド名・呼び元の型）の 3 つ組</strong>で行う。
 *
 * <h2>{@code reconstruct} を対象に含めない理由</h2>
 *
 * <p>永続化された状態からの復元は infrastructure のリポジトリが行う
 * （{@code MyBatisBookingRouteProposalRepository} /
 * {@code MyBatisTrackingActivityRepository}）。対象に含めると
 * 「永続化から集約を戻せない」という別の問題になる。
 * <strong>黙って外すのではなく、ここに書いて外している。</strong>
 *
 * <h2>検査できないもの（**黙って外さない**）</h2>
 *
 * <p><strong>リフレクション（{@code Method.invoke}）は検査できない。</strong>
 * バイトコードに呼び先の型もメソッド名も現れないためである。
 * {@code reconstruct} と同じく、<strong>外していることをここに書いて外す</strong>。
 *
 * <p>直接の呼び出しと<strong>メソッド参照</strong>（{@code ProposedRoute::withPriority}）は
 * どちらも検査する。メソッド参照は IT20 のクローズ前レビューまで<strong>素通りしていた</strong> ——
 * 契約は「呼び出しの形」ではなく「誰が生成・変更してよいか」であり、
 * <strong>形が違えば通るのは規則の穴である</strong>。
 *
 * <h2>テストからの直接呼び出しも許さない</h2>
 *
 * <p>本ルールはテストクラスを除外しない。{@code ProposedRoute} を確かめたいなら
 * {@code RouteSearchService} を通す。<strong>本番の経路を通らないテストは、
 * 本番で起きることを確かめていない。</strong>
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class EntityEncapsulationTest {

    /** ADR-024 の契約表の 1 行。 */
    record Contract(String ownerFqn, Set<String> methodNames, String allowedCallerFqn) {
    }

    private static final List<Contract> CONTRACTS = List.of(
            new Contract(
                    "com.example.cargotracker.routing.domain.model.entities.ProposedRoute",
                    Set.of("of", "withPriority"),
                    "com.example.cargotracker.routing.domain.model.RouteSearchService"),
            new Contract(
                    "com.example.cargotracker.tracking.domain.model.entities"
                            + ".TrackingExceptionEvent",
                    Set.of("raise", "resolve"),
                    "com.example.cargotracker.tracking.domain.model.aggregates"
                            + ".TrackingActivity"));

    @ArchTest
    static final ArchRule エンティティの生成と変更は契約した相手だけが呼ぶ =
            classes()
                    .should(契約外の相手から呼ばない())
                    .because("ADR-024 でサブパッケージへ分けた結果、javac が止めていた越境が"
                            + "止まらなくなった。呼んでよい相手はメソッドごとに定めてある"
                            + "（ADR-024「失ったもの」の表）");

    private static ArchCondition<JavaClass> 契約外の相手から呼ばない() {
        return new ArchCondition<>("契約外の相手から entities の生成・変更を呼ばない") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                // **呼び出しとメソッド参照の両方を見る**（IT20 クローズ前レビュー H1）。
                // getMethodCallsFromSelf() だけでは ProposedRoute::withPriority を
                // **拾わない**（レビューで実測）。契約は「呼び出しの形」ではなく
                // 「誰が生成・変更してよいか」であり、形が違えば通るのは規則の穴である
                Stream.concat(
                                item.getMethodCallsFromSelf().stream(),
                                item.getMethodReferencesFromSelf().stream())
                        .forEach(access -> CONTRACTS.stream()
                                .filter(contract -> 違反である(contract, access))
                                .findFirst()
                                .ifPresent(contract -> events.add(SimpleConditionEvent.violated(
                                        item, 違反の説明(contract, access)))));
            }
        };
    }

    /**
     * 契約に反する参照か。
     *
     * <p><strong>呼び出しとメソッド参照を同じ規則で判定する。</strong>
     * {@link JavaAccess} は両方の親であり、{@code getTarget()} /
     * {@code getOriginOwner()} は同じ形で取れる。
     */
    static boolean 違反である(Contract contract, JavaAccess<?> access) {
        var target = access.getTarget();
        if (!target.getOwner().getFullName().equals(contract.ownerFqn())) {
            return false;
        }
        if (!contract.methodNames().contains(target.getName())) {
            return false;
        }
        // 自分自身からの呼び出しは対象外。of が withPriority を呼ぶような
        // 型の内側の往来まで止めると、公開の可否と無関係な制約になる
        String callerFqn = access.getOriginOwner().getFullName();
        return !callerFqn.equals(contract.ownerFqn())
                && !callerFqn.equals(contract.allowedCallerFqn());
    }

    private static String 違反の説明(Contract contract, JavaAccess<?> access) {
        return "%s が %s.%s を参照している。呼んでよいのは %s だけである（ADR-024）。%s".formatted(
                access.getOriginOwner().getFullName(),
                contract.ownerFqn(),
                access.getTarget().getName(),
                contract.allowedCallerFqn(),
                access.getSourceCodeLocation());
    }

    /**
     * <strong>契約表に書いた型・メソッド・呼んでよい相手が実在すること</strong>（レビュー H2）。
     *
     * <p>{@code CONTRACTS} は<strong>文字列リテラルの名簿</strong>である。
     * {@code ProposedRoute} が改名・移動されたり {@code of} が {@code create} に
     * なった瞬間、{@link #違反である} は全件 {@code false} を返し、
     * <strong>検査は静かに緑のまま無力化する</strong>。
     *
     * <p><strong>名簿方式は「載っていないもの」を通す</strong>（ADR-015 で 3 IT 素通りした）。
     * 載っていないものは止められないが、<strong>載せたものがずれていないこと</strong>は固定できる。
     */
    @Test
    void 契約表の型とメソッドは実在する() {
        for (Contract contract : CONTRACTS) {
            Class<?> owner = 型を解決する(contract.ownerFqn());
            型を解決する(contract.allowedCallerFqn());

            Set<String> declared = Arrays.stream(owner.getDeclaredMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());

            assertThat(declared)
                    .as("契約表の %s に書いたメソッドが実在しない。改名されると"
                            + "この検査は静かに緑のまま無力化する（レビュー H2）",
                            contract.ownerFqn())
                    .containsAll(contract.methodNames());
        }
    }

    private static Class<?> 型を解決する(String fqn) {
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(
                    "契約表の型 %s が見つからない（レビュー H2）".formatted(fqn), e);
        }
    }

    /**
     * <strong>許可外から呼ぶと違反になる</strong>（レビュー H3）。
     *
     * <p>計画の成功基準は「<strong>許可外から呼ぶと赤になることを確かめる</strong>」である。
     * 手で壊して確かめただけでは<strong>証跡がリポジトリに残らない</strong>。
     * ArchUnit のルールは条件が一度も発火しなくても緑なので、
     * <strong>この検査自体が「入れたこと」しか確認されていない</strong>状態になる。
     *
     * <p><strong>安全装置は破るテストで固定する。</strong>
     */
    @Test
    void 許可外の相手からの参照を違反と判定する() {
        Contract contract = CONTRACTS.getFirst();

        assertThat(違反である(contract, 参照(contract.ownerFqn(), "of")))
                .as("**自分自身は違反ではない**")
                .isFalse();
        assertThat(違反である(contract, 参照(contract.allowedCallerFqn(), "of")))
                .as("**契約した相手は違反ではない**")
                .isFalse();
        assertThat(違反である(contract, 参照("com.example.cargotracker.Anything", "of")))
                .as("**それ以外からの参照は違反である**")
                .isTrue();
        assertThat(違反である(contract, 参照("com.example.cargotracker.Anything", "reconstruct")))
                .as("**reconstruct は契約表に無いので対象外**（復元は infrastructure が行う）")
                .isFalse();
        assertThat(違反である(contract, 参照("com.example.cargotracker.Anything", "raise")))
                .as("**別の型の同名メソッドを誤検知しない**（3 つ組で判定している）")
                .isFalse();
    }

    /** 判定だけを確かめるための、呼び元と呼び先だけを持つ偽の参照。 */
    private static JavaAccess<?> 参照(String callerFqn, String methodName) {
        JavaAccess<?> access = mock(JavaAccess.class, RETURNS_DEEP_STUBS);
        when(access.getOriginOwner().getFullName()).thenReturn(callerFqn);
        when(access.getTarget().getOwner().getFullName())
                .thenReturn(CONTRACTS.getFirst().ownerFqn());
        when(access.getTarget().getName()).thenReturn(methodName);
        return access;
    }
}
