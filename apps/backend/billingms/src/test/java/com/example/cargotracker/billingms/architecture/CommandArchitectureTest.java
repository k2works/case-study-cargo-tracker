package com.example.cargotracker.billingms.architecture;

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
 * billingms Command 構造ルール。
 *
 * <p>{@code *Command} クラスは必ず {@code @TargetEntityId} 付きフィールドを 1 つ以上持つ。</p>
 */
@AnalyzeClasses(packages = "com.example.cargotracker.billingms.domain.model.commands")
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
