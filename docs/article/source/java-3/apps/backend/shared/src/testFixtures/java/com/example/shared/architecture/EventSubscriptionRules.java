package com.example.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * 「購読しない」という決定を検査に落とす。
 *
 * <p><strong>否定の決定も検査に落とす。</strong>落とさなければ、あとから購読を足したとき
 * 「決定を意図的に覆したのか、写し漏れたのか」が区別できない。決定が 2 つで検査が 1 つ
 * なら、片方は文章のままである。
 */
public final class EventSubscriptionRules {

    private EventSubscriptionRules() {
    }

    /**
     * そのルーティングキーを購読していないことを検査する。
     *
     * <p>判定は<strong>キューを読む宣言</strong>（{@code @RabbitListener}）とルーティング
     * キーの文字列の両方が現れないことで行う。文字列だけを見ると、定数を経由した購読を
     * 見逃す。
     *
     * @param routingKey 購読していないはずのルーティングキー
     * @param reason なぜ購読しないのか。並べたまま放置されないよう、理由を書く
     */
    public static ArchRule doesNotSubscribeTo(String routingKey, String reason) {
        return classes()
                .should(new ArchCondition<JavaClass>(
                        "%s を購読しない（%s）".formatted(routingKey, reason)) {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean listens = javaClass.getMethods().stream()
                                .flatMap(method -> method.getAnnotations().stream())
                                .anyMatch(annotation -> RABBIT_LISTENER
                                        .equals(annotation.getRawType().getName()));
                        if (listens) {
                            events.add(SimpleConditionEvent.violated(javaClass,
                                    ("%s がイベントを購読している。%s を購読しないと"
                                            + "決めたはずである（%s）")
                                            .formatted(javaClass.getSimpleName(), routingKey,
                                                    reason)));
                        }
                    }
                })
                .as("%s を購読しない（%s）".formatted(routingKey, reason))
                .allowEmptyShould(true);
    }

    private static final String RABBIT_LISTENER =
            "org.springframework.amqp.rabbit.annotation.RabbitListener";
}
