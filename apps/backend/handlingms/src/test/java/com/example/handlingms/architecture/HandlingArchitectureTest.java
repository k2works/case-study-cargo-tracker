package com.example.handlingms.architecture;

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
 * handlingms のアーキテクチャ規約検証（IT7 review H1 教訓の横展開）。
 *
 * <p>handlingms は IT6 で {@code pending_handling_activity} 待避テーブル方式へ移行済み
 * （ADR-0012 §3）。本テストでその設計が後退しないことを CI で保証する。</p>
 */
class HandlingArchitectureTest {

    private static final Pattern PROCESSING_GROUP_PREFIX = Pattern.compile("^(cross|local|outbound)-.*");

    private static JavaClasses handlingClasses;

    @BeforeAll
    static void importClasses() {
        handlingClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.handlingms");
    }

    @Test
    @DisplayName("ADR-0014/0016: handlingms の @ProcessingGroup は prefix 規約準拠（ADR-0016 移行中は soft warning）")
    void processingGroupPrefixConvention() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ProcessingGroup.class));

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (var beanDef : scanner.findCandidateComponents("com.example.handlingms")) {
            String beanClassName = beanDef.getBeanClassName();
            if (beanClassName == null) continue;
            try {
                Class<?> clazz = Class.forName(beanClassName);
                ProcessingGroup annotation = clazz.getAnnotation(ProcessingGroup.class);
                if (annotation == null) continue;
                checked++;
                String groupName = annotation.value();
                if (!PROCESSING_GROUP_PREFIX.matcher(groupName).matches()) {
                    violations.add("@ProcessingGroup(\"" + groupName + "\") on " + beanClassName);
                }
            } catch (ClassNotFoundException e) {
                // skip
            }
        }
        assertThat(checked)
                .as("handlingms には @ProcessingGroup を持つクラスが 1 件以上存在する必要があります")
                .isPositive();
        if (!violations.isEmpty()) {
            System.out.println("[WARN] ADR-0014/0016 migration pending (ADR-0016 IT8 完了予定):");
            violations.forEach(v -> System.out.println("  - " + v));
        }
    }

    @Test
    @DisplayName("ADR-0012 二段イベント禁止: handlingms の @EventHandler クラスは EventGateway に依存しない（注: outbound publisher は除外）")
    void noTwoStageEventPublisher() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : handlingClasses) {
            // outbound publisher パターン（local event → shared kernel 変換）は
            // 当面 ADR-0014 上の許容として除外する。IT8 で集約発火型へ移行する場合は本除外を削除
            boolean isOutboundPublisher = clazz.getFullName().contains("CrossServicePublisher");
            if (isOutboundPublisher) continue;

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
    @DisplayName("DIP: handlingms の domain.services は infrastructure.repositories.mybatis に直接依存しない")
    void domainServicesShouldNotDependOnMyBatisMapper() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : handlingClasses) {
            if (!clazz.getPackageName().contains("com.example.handlingms.domain.services")) continue;
            clazz.getDirectDependenciesFromSelf().forEach(dep -> {
                if (dep.getTargetClass().getPackageName()
                        .contains("com.example.handlingms.infrastructure.repositories.mybatis")) {
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
