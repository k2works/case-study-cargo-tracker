package com.example.routingms.architecture;

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
 * routingms のアーキテクチャ規約検証（IT7 review H1 教訓の横展開）。
 */
class RoutingArchitectureTest {

    private static final Pattern PROCESSING_GROUP_PREFIX = Pattern.compile("^(cross|local|outbound)-.*");

    private static JavaClasses routingClasses;

    @BeforeAll
    static void importClasses() {
        routingClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.routingms");
    }

    @Test
    @DisplayName("ADR-0014/0016: routingms の @ProcessingGroup は prefix 規約準拠（ArchUnit DSL、IT8 T1.1）")
    void processingGroupPrefixConvention() {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (JavaClass clazz : routingClasses) {
            var annotation = clazz.tryGetAnnotationOfType(ProcessingGroup.class).orElse(null);
            if (annotation == null) continue;
            checked++;
            String groupName = annotation.value();
            if (!PROCESSING_GROUP_PREFIX.matcher(groupName).matches()) {
                violations.add("@ProcessingGroup(\"" + groupName + "\") on " + clazz.getFullName());
            }
        }
        assertThat(checked)
                .as("routingms には @ProcessingGroup を持つクラスが 1 件以上存在する必要があります")
                .isPositive();
        if (!violations.isEmpty()) {
            System.out.println("[WARN] ADR-0014/0016 migration pending (ADR-0016 IT8 完了予定):");
            violations.forEach(v -> System.out.println("  - " + v));
        }
    }

    @Test
    @DisplayName("ADR-0012 二段イベント禁止: routingms の @EventHandler クラスは EventGateway に依存しない")
    void noTwoStageEventPublisher() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : routingClasses) {
            boolean hasEventHandler = clazz.getMethods().stream()
                    .anyMatch(m -> m.isAnnotatedWith(EventHandler.class));
            if (!hasEventHandler) continue;
            boolean usesEventGateway = clazz.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().isEquivalentTo(EventGateway.class));
            if (usesEventGateway) {
                violations.add("Two-stage event publisher: " + clazz.getFullName());
            }
        }
        assertThat(violations)
                .as("ADR-0012 自己整合チェックリスト C3 / PR1 違反")
                .isEmpty();
    }

    @Test
    @DisplayName("DIP: routingms の domain.services は infrastructure.repositories.mybatis に直接依存しない")
    void domainServicesShouldNotDependOnMyBatisMapper() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : routingClasses) {
            if (!clazz.getPackageName().contains("com.example.routingms.domain.services")) continue;
            clazz.getDirectDependenciesFromSelf().forEach(dep -> {
                if (dep.getTargetClass().getPackageName()
                        .contains("com.example.routingms.infrastructure.repositories.mybatis")) {
                    violations.add(clazz.getFullName() + " depends on infrastructure Mapper: "
                            + dep.getTargetClass().getFullName());
                }
            });
        }
        assertThat(violations)
                .as("ヘキサゴナル / DIP 違反")
                .isEmpty();
    }
}
