package com.example.bookingms.architecture;

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
 * bookingms のアーキテクチャ規約検証（ADR-0012 / ADR-0014 / ADR-0016 自動検知）。
 *
 * <p>IT7 review H1 教訓を bookingms に横展開（commit bf020c3e の billingms 版から派生）。
 * Saga / cross-service ハンドラの追加時に二段イベント・prefix 規約違反・DIP 違反を CI で検知。</p>
 *
 * <p>注意: bookingms には Saga（BookingSagaManager）が存在し、Saga は仕様上 EventBus に
 * Command を投げるが本テストの「二段イベント」検査対象外（@SagaEventHandler は @EventHandler
 * と別アノテーション、CommandGateway 経由は EventGateway とは別 Bean）。</p>
 */
class BookingArchitectureTest {

    /** ADR-0014/0016 命名規約の prefix。 */
    private static final Pattern PROCESSING_GROUP_PREFIX = Pattern.compile("^(cross|local|outbound)-.*");

    private static JavaClasses bookingClasses;

    @BeforeAll
    static void importClasses() {
        bookingClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.bookingms");
    }

    @Test
    @DisplayName("ADR-0014/0016: bookingms の @ProcessingGroup は cross-/local-/outbound- prefix を持つ（ArchUnit DSL、IT8 T1.1）")
    void processingGroupPrefixConvention() {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (JavaClass clazz : bookingClasses) {
            var annotation = clazz.tryGetAnnotationOfType(ProcessingGroup.class).orElse(null);
            if (annotation == null) continue;
            checked++;
            String groupName = annotation.value();
            if (!PROCESSING_GROUP_PREFIX.matcher(groupName).matches()) {
                violations.add("@ProcessingGroup(\"" + groupName + "\") on " + clazz.getFullName()
                        + " does not match prefix convention");
            }
        }
        assertThat(checked)
                .as("bookingms には @ProcessingGroup を持つクラスが 1 件以上存在する必要があります")
                .isPositive();
        // IT8 T1.2 で ADR-0016 全 10 グループ改名完了。soft warning → hard assertion に変更
        assertThat(violations)
                .as("ADR-0014/0016 命名規約違反（IT8 移行完了済み）")
                .isEmpty();
    }

    @Test
    @DisplayName("ADR-0012 二段イベント禁止: bookingms の @EventHandler クラスは EventGateway に依存しない")
    void noTwoStageEventPublisher() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : bookingClasses) {
            boolean hasEventHandler = clazz.getMethods().stream()
                    .anyMatch(m -> m.isAnnotatedWith(EventHandler.class));
            if (!hasEventHandler) continue;
            boolean usesEventGateway = clazz.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().isEquivalentTo(EventGateway.class));
            if (usesEventGateway) {
                violations.add("Two-stage event publisher: " + clazz.getFullName()
                        + " has @EventHandler methods and depends on EventGateway. "
                        + "ADR-0012 違反。集約発火型に統合してください。");
            }
        }
        assertThat(violations)
                .as("ADR-0012 自己整合チェックリスト C3 / PR1 違反")
                .isEmpty();
    }

    @Test
    @DisplayName("DIP: bookingms の domain.services は infrastructure.repositories.mybatis に直接依存しない")
    void domainServicesShouldNotDependOnMyBatisMapper() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : bookingClasses) {
            if (!clazz.getPackageName().contains("com.example.bookingms.domain.services")) continue;
            clazz.getDirectDependenciesFromSelf().forEach(dep -> {
                if (dep.getTargetClass().getPackageName()
                        .contains("com.example.bookingms.infrastructure.repositories.mybatis")) {
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
