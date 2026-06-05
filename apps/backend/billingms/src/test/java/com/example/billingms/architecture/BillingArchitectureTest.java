package com.example.billingms.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * billingms のアーキテクチャ規約検証（ADR-0012 / ADR-0014 / ADR-0016 自動検知）。
 *
 * <p>IT7 review H1（二段イベント問題）の再発を ArchUnit で構造的に防止する。
 * 規約違反は CI（{@code ./gradlew :billingms:check}）で fail する。</p>
 *
 * <p><strong>注意</strong>: ArchUnit 1.4.0 は JDK 25 のクラスファイル major version 69 を
 * 完全サポートしていない（一部の enum 等で読み込み失敗）。バイトコードに依存しない
 * パッケージ依存ルール（DIP テスト）と、{@code @EventHandler} アノテーション検査（二段イベント禁止）は
 * 動作する。{@code @ProcessingGroup} の value（文字列）まで踏み込んだ検査は
 * Spring の {@link ClassPathScanningCandidateComponentProvider} を用いてリフレクションベースで実施する。</p>
 *
 * <p>ArchUnit 1.5+ または ASM 更新後の IT8 で {@code processingGroupPrefixConvention} を
 * ArchUnit ベースに統一する予定。</p>
 */
class BillingArchitectureTest {

    /** ADR-0014/0016 命名規約の prefix。 */
    private static final Pattern PROCESSING_GROUP_PREFIX = Pattern.compile("^(cross|local|outbound)-.*");

    private static JavaClasses billingClasses;

    @BeforeAll
    static void importClasses() {
        billingClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.billingms");
    }

    @Test
    @DisplayName("ADR-0014/0016: すべての @ProcessingGroup は cross-/local-/outbound- prefix を持つ（Spring scan）")
    void processingGroupPrefixConvention() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ProcessingGroup.class));

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (var beanDef : scanner.findCandidateComponents("com.example.billingms")) {
            String beanClassName = beanDef.getBeanClassName();
            if (beanClassName == null) continue;
            try {
                Class<?> clazz = Class.forName(beanClassName);
                ProcessingGroup annotation = clazz.getAnnotation(ProcessingGroup.class);
                if (annotation == null) continue;
                checked++;
                String groupName = annotation.value();
                if (!PROCESSING_GROUP_PREFIX.matcher(groupName).matches()) {
                    violations.add("@ProcessingGroup(\"" + groupName + "\") on " + beanClassName
                            + " does not match prefix convention (cross-/local-/outbound-)");
                }
            } catch (ClassNotFoundException e) {
                // skip
            }
        }
        assertThat(checked)
                .as("billingms には @ProcessingGroup を持つクラスが 1 件以上存在する必要があります")
                .isPositive();
        assertThat(violations)
                .as("ADR-0014/0016 命名規約違反")
                .isEmpty();
    }

    @Test
    @DisplayName("ADR-0012 二段イベント禁止: @EventHandler を持つクラスは EventGateway に依存しない")
    void noTwoStageEventPublisher() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : billingClasses) {
            boolean hasEventHandler = clazz.getMethods().stream()
                    .anyMatch(m -> m.isAnnotatedWith(EventHandler.class));
            if (!hasEventHandler) continue;
            boolean usesEventGateway = clazz.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().isEquivalentTo(EventGateway.class));
            if (usesEventGateway) {
                violations.add("Two-stage event publisher: " + clazz.getFullName()
                        + " has @EventHandler methods and depends on EventGateway. "
                        + "ADR-0012 違反。集約発火型（AggregateLifecycle.apply）に統合してください。");
            }
        }
        assertThat(violations)
                .as("ADR-0012 自己整合チェックリスト C3 / PR1 違反（IT7 review H1 の再発リスク）")
                .isEmpty();
    }

    @Test
    @DisplayName("DIP: domain.services は infrastructure.repositories.mybatis に直接依存しない（review M2）")
    void domainServicesShouldNotDependOnMyBatisMapper() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : billingClasses) {
            if (!clazz.getPackageName().contains("com.example.billingms.domain.services")) continue;
            clazz.getDirectDependenciesFromSelf().forEach(dep -> {
                if (dep.getTargetClass().getPackageName()
                        .contains("com.example.billingms.infrastructure.repositories.mybatis")) {
                    violations.add(clazz.getFullName() + " depends on infrastructure Mapper: "
                            + dep.getTargetClass().getFullName()
                            + ". IT7 review M2 教訓: ドメイン側にポート定義 + infrastructure 側で実装すること。");
                }
            });
        }
        assertThat(violations)
                .as("ヘキサゴナル / DIP 違反（review M2 の再発）")
                .isEmpty();
    }
}
