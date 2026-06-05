package com.example.trackingms.architecture;

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
 * trackingms のアーキテクチャ規約検証（ADR-0012 / ADR-0014 / ADR-0016 自動検知）。
 *
 * <p>IT7 review H1 教訓を trackingms に横展開。trackingms は IT6 で
 * CargoDeliveredEventPublisher（二段イベント）を集約発火型に統合済み（ADR-0012 §2）。
 * 本テストでその設計が後退しないことを CI で保証する。</p>
 */
class TrackingArchitectureTest {

    private static final Pattern PROCESSING_GROUP_PREFIX = Pattern.compile("^(cross|local|outbound)-.*");

    private static JavaClasses trackingClasses;

    @BeforeAll
    static void importClasses() {
        trackingClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.trackingms");
    }

    @Test
    @DisplayName("ADR-0014/0016: trackingms の @ProcessingGroup は prefix 規約準拠（ArchUnit DSL、IT8 T1.1）")
    void processingGroupPrefixConvention() {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (JavaClass clazz : trackingClasses) {
            var annotation = clazz.tryGetAnnotationOfType(ProcessingGroup.class).orElse(null);
            if (annotation == null) continue;
            checked++;
            String groupName = annotation.value();
            if (!PROCESSING_GROUP_PREFIX.matcher(groupName).matches()) {
                violations.add("@ProcessingGroup(\"" + groupName + "\") on " + clazz.getFullName());
            }
        }
        assertThat(checked)
                .as("trackingms には @ProcessingGroup を持つクラスが 1 件以上存在する必要があります")
                .isPositive();
        // IT8 T1.2 で ADR-0016 全 10 グループ改名完了。soft warning → hard assertion に変更
        assertThat(violations)
                .as("ADR-0014/0016 命名規約違反（IT8 移行完了済み）")
                .isEmpty();
    }

    @Test
    @DisplayName("ADR-0012 二段イベント禁止: trackingms の @EventHandler クラスは EventGateway に依存しない（IT6 CargoDeliveredEventPublisher 廃止、IT8 T1.10 で CargoTrackedEventPublisher も廃止完了）")
    void noTwoStageEventPublisher() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : trackingClasses) {
            boolean hasEventHandler = clazz.getMethods().stream()
                    .anyMatch(m -> m.isAnnotatedWith(EventHandler.class));
            if (!hasEventHandler) continue;
            boolean usesEventGateway = clazz.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().isEquivalentTo(EventGateway.class));
            if (usesEventGateway) {
                violations.add("Two-stage event publisher: " + clazz.getFullName()
                        + " has @EventHandler and EventGateway. ADR-0012 違反。"
                        + "IT6 で CargoDeliveredEventPublisher を廃止済み。後退させないこと。");
            }
        }
        assertThat(violations)
                .as("ADR-0012 自己整合チェックリスト C3 / PR1 違反（IT7 review H1 ＋ IT6 H3 教訓）")
                .isEmpty();
    }

    @Test
    @DisplayName("DIP: trackingms の domain.services は infrastructure.repositories.mybatis に直接依存しない")
    void domainServicesShouldNotDependOnMyBatisMapper() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : trackingClasses) {
            if (!clazz.getPackageName().contains("com.example.trackingms.domain.services")) continue;
            clazz.getDirectDependenciesFromSelf().forEach(dep -> {
                if (dep.getTargetClass().getPackageName()
                        .contains("com.example.trackingms.infrastructure.repositories.mybatis")) {
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
