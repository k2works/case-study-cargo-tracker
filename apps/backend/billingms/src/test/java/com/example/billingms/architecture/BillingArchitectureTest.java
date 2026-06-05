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
 * <p>ArchUnit 1.4.2 で JDK 25 クラスファイル major version 69 が完全サポートされたため
 * （IT8 T1.1 で 1.4.0 → 1.4.2 にアップグレード済み）、すべての検査を ArchUnit DSL ベースに統一。
 * IT7 で Spring scan による回避策を使っていた {@code processingGroupPrefixConvention} も
 * ArchUnit の {@code JavaClass.tryGetAnnotationOfType} に統一した。</p>
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
    @DisplayName("ADR-0014/0016: すべての @ProcessingGroup は cross-/local-/outbound- prefix を持つ（ArchUnit DSL、IT8 T1.1 で Spring scan 撤去）")
    void processingGroupPrefixConvention() {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (JavaClass clazz : billingClasses) {
            var annotation = clazz.tryGetAnnotationOfType(ProcessingGroup.class).orElse(null);
            if (annotation == null) continue;
            checked++;
            String groupName = annotation.value();
            if (!PROCESSING_GROUP_PREFIX.matcher(groupName).matches()) {
                violations.add("@ProcessingGroup(\"" + groupName + "\") on " + clazz.getFullName()
                        + " does not match prefix convention (cross-/local-/outbound-)");
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
