package com.example.cargotracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;

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
 * <h2>テストからの直接呼び出しも許さない</h2>
 *
 * <p>本ルールはテストクラスを除外しない。{@code ProposedRoute} を確かめたいなら
 * {@code RouteSearchService} を通す。<strong>本番の経路を通らないテストは、
 * 本番で起きることを確かめていない。</strong>
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class EntityEncapsulationTest {

    /** ADR-024 の契約表の 1 行。 */
    private record Contract(String ownerFqn, Set<String> methodNames, String allowedCallerFqn) {
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
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    CONTRACTS.stream()
                            .filter(contract -> 違反である(contract, call))
                            .findFirst()
                            .ifPresent(contract -> events.add(SimpleConditionEvent.violated(
                                    item, 違反の説明(contract, call))));
                }
            }
        };
    }

    private static boolean 違反である(Contract contract, JavaMethodCall call) {
        var target = call.getTarget();
        if (!target.getOwner().getFullName().equals(contract.ownerFqn())) {
            return false;
        }
        if (!contract.methodNames().contains(target.getName())) {
            return false;
        }
        // 自分自身からの呼び出しは対象外。of が withPriority を呼ぶような
        // 型の内側の往来まで止めると、公開の可否と無関係な制約になる
        String callerFqn = call.getOriginOwner().getFullName();
        return !callerFqn.equals(contract.ownerFqn())
                && !callerFqn.equals(contract.allowedCallerFqn());
    }

    private static String 違反の説明(Contract contract, JavaMethodCall call) {
        return "%s が %s.%s を呼んでいる。呼んでよいのは %s だけである（ADR-024）。%s".formatted(
                call.getOriginOwner().getFullName(),
                contract.ownerFqn(),
                call.getTarget().getName(),
                contract.allowedCallerFqn(),
                call.getSourceCodeLocation());
    }
}
