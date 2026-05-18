package com.example.cargotracker.handlingms.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.axonframework.modelling.annotation.TargetEntityId;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 4 サービス共通の Command 構造ルール（IT6 TI05-1.3 で共通化）。
 *
 * <p>{@code *Command} クラスは必ず {@code @TargetEntityId} 付きフィールドを 1 つ以上持つ。
 * Axon の CommandGateway が Aggregate にルーティングできなくなる事故を防ぐ。</p>
 *
 * <p>参考: docs/review/IT4_bugfix_review_20260518.md H1 / docs/development/iteration_plan-6.md TI05-1.3</p>
 */
@AnalyzeClasses(packages = "com.example.cargotracker.handlingms.domain.model.commands")
class CommandArchitectureTest {

    @ArchTest
    static final ArchRule commandsMustHaveTargetEntityId =
            classes()
                    .that().haveSimpleNameEndingWith("Command")
                    .should(haveTargetEntityIdField())
                    .because("Axon の CommandGateway が Aggregate にルーティングするには "
                            + "@TargetEntityId が必須（IT4 障害 H1 再発防止）")
                    .allowEmptyShould(true);

    private static ArchCondition<JavaClass> haveTargetEntityIdField() {
        return new ArchCondition<>("have a field annotated with @TargetEntityId") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasTargetEntityId = javaClass.getFields().stream()
                        .anyMatch(CommandArchitectureTest::isTargetEntityIdField);
                if (!hasTargetEntityId) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                    "Command class %s does not declare any field annotated with @TargetEntityId",
                                    javaClass.getName())));
                }
            }
        };
    }

    private static boolean isTargetEntityIdField(JavaField field) {
        return field.isAnnotatedWith(TargetEntityId.class);
    }
}
