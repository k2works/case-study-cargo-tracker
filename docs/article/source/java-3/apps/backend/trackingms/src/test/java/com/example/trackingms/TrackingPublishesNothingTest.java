package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * trackingms は<strong>イベントを 1 本も発行しない</strong>（[ADR-024] 決定 8）。
 *
 * <p>{@code TrackingExceptionDetectedEvent} も {@code CargoDeliveredEvent} も出さない。
 * 本 IT の通知は代替であり、配信する相手がいない——購読者のいないイベントを先に出すと、
 * 契約だけが増えて誰も守らない。
 *
 * <p><strong>発行しないという決定も検査に落とす。</strong>文章のまま置くと、あとから
 * 静かに増える（[ADR-022] 決定 1・[ADR-023] 決定 5 と同じ形）。
 *
 * <p>ポートの形ではなく<strong>発行の呼び出し箇所を数える</strong>——ポートに足さずに
 * メッセージ基盤を直接呼べば、ポートを見るだけの検査は迂回できる（IT8 返済枠 0.10）。
 */
@DisplayName("trackingms は発行しない（ADR-024 決定 8）")
class TrackingPublishesNothingTest {

    @Test
    @DisplayName("メッセージを送り出す呼び出しが 1 つも無い")
    void hasNoPublishingCallSite() {
        List<String> publishers = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.trackingms").stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(TrackingPublishesNothingTest::publishes)
                .map(method -> method.getOwner().getSimpleName() + "#" + method.getName())
                .sorted()
                .toList();

        assertThat(publishers)
                .as("trackingms がイベントを発行している。[ADR-024] 決定 8 は"
                        + "「1 本も出さない」と決めている。出すなら決定を書き直すこと")
                .isEmpty();
    }

    /** メッセージ基盤へ送り出しているか。型名でも名前でもなく、送信のメソッドで見る。 */
    private static boolean publishes(JavaMethod method) {
        return method.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getPackageName()
                                .startsWith("org.springframework.amqp")
                        && (call.getName().startsWith("convertAndSend")
                                || call.getName().equals("send")));
    }
}
