package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.internal.commandservices.AdvanceBookingUseCase;
import com.example.shared.contract.HandlingActivityRegisteredContract;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷役のイベントを購読する範囲（[ADR-025] 決定 1・[ADR-023] 決定 6 の更新）。
 *
 * <p>IT7 の {@code HandlingEventNotSubscribedTest} は「bookingms は購読しない」を守って
 * いた。<strong>US30 で購読した</strong>——輸送中を知る手段が他に無く、予約一覧は船に
 * 載った貨物を「受領待ち」と出し続けていたためである。
 *
 * <p><strong>決定 6 の本質は「購読しないこと」ではなく「{@code RoutingStatus} を
 * 動かさないこと」である。</strong>誤配で経路の状態を動かすのは US28（IT10）であり、
 * そこはまだ来ていない。守る対象を、購読の有無から<strong>何を動かすか</strong>へ移す。
 *
 * <p>この検査が落ちたら、それは<strong>US28 に着手した合図</strong>である。
 */
@DisplayName("荷役のイベントで動かす範囲（ADR-025 決定 1）")
class HandlingEventSubscriptionScopeTest {

    private static final Path USE_CASE = Path.of(
            "src/main/java/com/example/bookingms/application/internal/commandservices/AdvanceBookingUseCase.java");

    /**
     * 経路の状態に関わる語。
     *
     * <p>型を持っていない箇所もあるので、<strong>語が現れないこと</strong>で見る。
     * 粗いが、動かす実装を書けば必ずどれかが現れる（handlingms の
     * {@code RoutingStatusNotTouchedTest} と同じ形）。
     */
    private static final List<String> ROUTING_STATUS_WORDS =
            List.of("MISROUTED", "RoutingStatus", "routingStatus");

    /** 購読は契約のルーティングキーで結びつける。**綴りを写し間違えると黙って届かない**。 */
    @Test
    @DisplayName("契約のルーティングキーで購読している")
    void subscribesWithTheContractRoutingKey() {
        String channels = read(Path.of(
                "src/main/java/com/example/bookingms/infrastructure/acl/"
                        + "CargoEventChannels.java"));

        assertThat(channels)
                .as("契約と違うルーティングキーで結びつけている。送り手はエラーにならないまま届かない")
                .contains("\"" + HandlingActivityRegisteredContract.ROUTING_KEY + "\"");
    }

    /**
     * <strong>動かすのは予約の状態だけである。</strong>
     *
     * <p>誤配で {@code RoutingStatus} を動かすのは US28（IT10）。ここで動かすと、
     * 経路設計者の一覧に「誤配」が現れるのに再設計の入口が無い状態になる。
     */
    @Test
    @DisplayName("経路の状態は動かさない（US28・IT10 まで）")
    void doesNotTouchRoutingStatus() {
        String source = read(USE_CASE);

        assertThat(ROUTING_STATUS_WORDS.stream().filter(source::contains).toList())
                .as("経路の状態を動かしている。US28 に着手したなら ADR-025 を更新すること")
                .isEmpty();
    }

    /**
     * <strong>ACL を引かない。</strong>
     *
     * <p>イベントが運ぶものだけで進める。相手のドメインを読みに行くと、決定 1 が
     * 避けた「2 ホップ先からの伝聞」に戻る。
     */
    @Test
    @DisplayName("荷役の記録を読みに行かない")
    void doesNotCallBackIntoHandling() {
        // **コメントは検査の対象外。**「handlingms が判定を済ませている」と説明することは
        // 正当であり、むしろ書くべきである——読みに行っているかどうかは
        // **import と呼び出し**に現れる（IT10 で説明文が引っかかった）
        String source = stripComments(read(USE_CASE));

        assertThat(source)
                .as("handlingms を読みに行っている。イベントが運ぶもので足りるはず")
                .doesNotContain("handlingms");
    }

    /** ブロックコメントと行コメントを外す。**説明を書くことを罰しない**。 */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /** 進める入口は 1 つだけ。増えると、冪等と巻き戻さない守りを写す先が増える。 */
    @Test
    @DisplayName("予約を進める入口は 1 つだけ")
    void hasASingleEntryPoint() {
        List<String> methods = Arrays.stream(AdvanceBookingUseCase.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();

        assertThat(methods).containsExactly("advance");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
