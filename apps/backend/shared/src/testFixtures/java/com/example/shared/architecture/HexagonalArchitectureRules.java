package com.example.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/**
 * 全サービス共通のアーキテクチャ規則。
 *
 * <p>各サービスの ArchitectureTest から呼び出す。規則をここに集約することで、
 * サービスごとに検査内容がずれる（＝一部だけ緩い）状態を防ぐ。
 * 適用漏れ自体は {@code ArchitectureRuleCoverageTest}（shared のメタテスト）が検出する。
 */
public final class HexagonalArchitectureRules {

    private HexagonalArchitectureRules() {
    }

    /**
     * ヘキサゴナル 4 層（domain / application / infrastructure / interfaces）の依存方向を検査する。
     * 依存は常に外から内へ向かう。domain は誰にも依存しない。
     */
    public static List<ArchRule> layerRules(String basePackage) {
        String domain = basePackage + ".domain..";
        String application = basePackage + ".application..";
        String infrastructure = basePackage + ".infrastructure..";
        String interfaces = basePackage + ".interfaces..";

        return List.of(
                noClasses().that().resideInAPackage(domain)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(application, infrastructure, interfaces)
                        .as("domain は他のどの層にも依存しない")
                        .allowEmptyShould(true),
                noClasses().that().resideInAPackage(application)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(infrastructure, interfaces)
                        .as("application は infrastructure と interfaces に依存しない")
                        .allowEmptyShould(true),
                noClasses().that().resideInAPackage(infrastructure)
                        .should().dependOnClassesThat().resideInAPackage(interfaces)
                        .as("infrastructure は interfaces に依存しない")
                        .allowEmptyShould(true),
                noClasses().that().resideInAPackage(domain)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.apache.ibatis..")
                        .as("domain はフレームワークに依存しない（純粋な業務ロジックに保つ）")
                        .allowEmptyShould(true));
    }

    /**
     * サービス境界の独立性を検査する。他サービスのパッケージを直接参照してはならない。
     * サービス間の連携は HTTP / メッセージング経由であり、共有は shared（共有カーネル）に限る。
     */
    public static ArchRule serviceIsolationRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "他サービスのパッケージ",
                                javaClass -> javaClass.getPackageName().startsWith("com.example.")
                                        && !javaClass.getPackageName().startsWith("com.example." + serviceName)
                                        && !javaClass.getPackageName().startsWith("com.example.shared")))
                .as("他サービスのクラスを直接参照しない（連携は HTTP / メッセージング経由）")
                .allowEmptyShould(true);
    }

    /**
     * ADR-004 の分担を検査する。署名検証は gatewayms に一元化し、各サービスは
     * Gateway が付与した検証済みクレームのロールだけを見る。
     *
     * <p>この規則が無いと「Spring Security を素直に入れたら署名検証まで付いてきた」という形で
     * 鍵の管理が 7 サービスに拡散する。ADR-004 が最も恐れる失敗モードを構造で止める。
     *
     * <p>gatewayms（検証）と authms（発行）は鍵を持つ 2 サービスであり、この規則の対象外とする。
     * authms 側は代わりに {@link #noTokenVerificationRule(String)} で「発行はするが検証はしない」
     * ことを検査する。依存の有無だけで判断すると、発行のために入れたライブラリで検証も
     * 始められてしまう。
     */
    public static ArchRule noJwtDependencyRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should().dependOnClassesThat().resideInAnyPackage("io.jsonwebtoken..")
                .as("gatewayms / authms 以外は JWT ライブラリに依存しない（ADR-004）")
                .allowEmptyShould(true);
    }

    /**
     * トークンの検証（パース）を行っていないことを検査する。
     *
     * <p>authms は発行のために JWT ライブラリを持つが、検証は行わない。依存の有無ではなく
     * 「検証の入口を呼んでいないこと」で判定する。
     */
    public static ArchRule noTokenVerificationRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should(new ArchCondition<JavaClass>("JWT の検証 API を呼ぶ") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getMethodCallsFromSelf().stream()
                                .filter(call -> call.getTargetOwner().getName().startsWith("io.jsonwebtoken")
                                        && VERIFICATION_METHODS.contains(call.getName()))
                                .forEach(call -> events.add(SimpleConditionEvent.violated(
                                        javaClass, call.getDescription())));
                    }
                })
                .as("署名検証は gatewayms だけが行う（ADR-004）")
                .allowEmptyShould(true);
    }

    /** JWT の検証を始める入口となるメソッド名。 */
    private static final java.util.Set<String> VERIFICATION_METHODS =
            java.util.Set.of("parser", "parserBuilder", "parseSignedClaims", "parseClaimsJws");

    /** サービスの本番クラス（テストを除く）を読み込む。 */
    public static JavaClasses importProductionClasses(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }
}
