package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.port.CargoCancelled;
import com.example.bookingms.application.port.CargoEventNotifier;
import com.example.shared.contract.CargoCancelledContract;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 発行するイベントの範囲と形（[ADR-025] 決定 3）。
 *
 * <p><strong>読む側の無い配線を先に敷かない。</strong>billingms へのキャンセル料の
 * 算定は US21（IT11）であり、受け口が無い。発行だけ先に足すと、誰も読まないメッセージが
 * ブローカーに溜まり、<strong>「動いているように見えて何も起きない」</strong>状態になる。
 */
@DisplayName("予約のイベントの発行")
class CargoEventPublishingTest {

    private static final Path NOTIFIER = Path.of(
            "src/main/java/com/example/bookingms/infrastructure/messaging/"
                    + "RabbitCargoEventNotifier.java");

    /**
     * <strong>発行するのは 2 種類だけである。</strong>
     *
     * <p>ポートのメソッドを数える。増やした瞬間に赤になり、
     * 「決定を意図的に覆したのか、勢いで足したのか」を立ち止まって考えることになる。
     */
    @Test
    @DisplayName("発行するイベントは 2 種類だけ")
    void publishesOnlyTwoKindsOfEvents() {
        List<String> published = Arrays.stream(CargoEventNotifier.class.getDeclaredMethods())
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(published)
                .as("発行するイベントが増えている。読む側があるかを確かめること"
                        + "（[ADR-025] 決定 3）")
                .containsExactly("cargoCancelled", "trackingNumberIssued");
    }

    /**
     * <strong>理由を載せない。</strong>
     *
     * <p>このイベントが行き着く先は公開の追跡照会——認証の無い画面である。社内の判断を、
     * 追跡番号を手に入れた誰もが読める場所へ流さない。
     *
     * <p><strong>「載せる項目」だけを見ない。</strong>一覧が揃っていても、余分な項目が
     * 足されていれば気づけない。<strong>載せてはいけない項目</strong>も対で見る。
     */
    @Test
    @DisplayName("キャンセルのイベントに、社内の判断を載せない")
    void doesNotCarryInternalJudgement() {
        List<String> fields = Arrays.stream(CargoCancelled.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fields)
                .as("契約と項目が食い違っている。送り手はエラーにならないまま、"
                        + "受け手が読めない形になる")
                .isEqualTo(CargoCancelledContract.FIELDS);
        assertThat(fields)
                .as("公開の追跡照会に流れる経路に、社内の判断が載っている")
                .doesNotContainAnyElementsOf(CargoCancelledContract.FORBIDDEN_FIELDS);
    }

    /**
     * <strong>billingms へは発行しない。</strong>
     *
     * <p>発行の呼び出し箇所を数える（[ADR-024] 決定 8 と同じ形）。交換機とルーティングキーの
     * 組を数え、決定した 2 本以外が現れないことを見る。
     */
    @Test
    @DisplayName("送り先は、決めた 2 本のルーティングキーだけ")
    void sendsOnlyToTheAgreedRoutingKeys() {
        String source = stripComments(read(NOTIFIER));

        List<String> routingKeys = Arrays.stream(source.split("\n"))
                .filter(line -> line.contains("CargoEventChannels."))
                .filter(line -> !line.contains("CargoEventChannels.EXCHANGE"))
                .map(String::trim)
                .toList();

        assertThat(routingKeys)
                .as("決めた 2 本以外へ送っている。読む側があるかを確かめること")
                .hasSize(2);
        assertThat(source)
                .as("billingms 向けの交換機へ直接送っている")
                .doesNotContain("billing");
    }

    /** コメントは検査の対象外。説明を書くことを罰しない。 */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\n]*", "");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
