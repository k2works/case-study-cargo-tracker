package com.example.cargotracker.bookingms.architecture;

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
 * IT4 バグ修正レビュー H1 対応: {@code @TargetEntityId} 未付与コマンドを CI で検出する ArchUnit ルール。
 *
 * <p>IT4 の本番障害（{@code AssignRouteToCargoCommand} の {@code @TargetEntityId} 欠落により
 * Axon がコマンドを Aggregate にルーティングできなかった）を再発させないため、
 * {@code *Command} で終わるレコード/クラスは必ず {@code @TargetEntityId} アノテーション付きの
 * フィールドを 1 つ以上持たなければならない。</p>
 *
 * <p>参考: docs/review/IT4_bugfix_review_20260518.md H1</p>
 */
@AnalyzeClasses(packages = "com.example.cargotracker.bookingms.domain.model.commands")
class CommandArchitectureTest {

    @ArchTest
    static final ArchRule commandsMustHaveTargetEntityId =
            classes()
                    .that().haveSimpleNameEndingWith("Command")
                    .should(haveTargetEntityIdField())
                    .because("Axon の CommandGateway が Aggregate にルーティングするには "
                            + "@TargetEntityId が必須（IT4 障害 H1 再発防止）");

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
