package com.example.cargotracker.trackingms.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 4 サービス共通の {@code @CommandHandler} 強制ルール（IT6 TI05-1.3 で共通化）。
 *
 * <p>{@code domain.model.aggregates} 配下の Aggregate クラスに定義されたメソッドのうち、
 * いずれかの引数型が {@code *Command} で終わるものは {@link CommandHandler} 必須。</p>
 *
 * <p>参考: docs/development/retrospective-5.md P1</p>
 */
@AnalyzeClasses(packages = "com.example.cargotracker.trackingms.domain.model.aggregates")
class CommandHandlerArchitectureTest {

    @ArchTest
    static final ArchRule commandHandlersMustBeAnnotated =
            classes()
                    .that().resideInAPackage("..domain.model.aggregates..")
                    .should(haveCommandHandlerAnnotationOnAllCommandMethods())
                    .because("Axon の Command ルーティングには @CommandHandler 注釈が必須 "
                            + "（IT5 P1 再発防止）")
                    .allowEmptyShould(true);

    private static ArchCondition<JavaClass> haveCommandHandlerAnnotationOnAllCommandMethods() {
        return new ArchCondition<>("have @CommandHandler on every method accepting a *Command argument") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    if (acceptsCommandArgument(method) && !method.isAnnotatedWith(CommandHandler.class)) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                String.format(
                                        "Aggregate method %s.%s accepts a *Command argument "
                                                + "but is not annotated with @CommandHandler",
                                        javaClass.getSimpleName(), method.getName())));
                    }
                }
            }
        };
    }

    private static boolean acceptsCommandArgument(JavaMethod method) {
        return method.getRawParameterTypes().stream()
                .map(JavaClass::getName)
                .anyMatch(name -> name.endsWith("Command"));
    }
}
