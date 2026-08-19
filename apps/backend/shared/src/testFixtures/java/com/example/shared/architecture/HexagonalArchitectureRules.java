package com.example.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
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
     */
    public static ArchRule noJwtVerificationRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should().dependOnClassesThat().resideInAnyPackage("io.jsonwebtoken..")
                .as("gatewayms 以外は JWT の署名検証を行わない（ADR-004）")
                .allowEmptyShould(true);
    }

    /** サービスの本番クラス（テストを除く）を読み込む。 */
    public static JavaClasses importProductionClasses(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }
}
