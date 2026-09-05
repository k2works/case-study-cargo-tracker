package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 問い合わせの包み方（IT4 の返済 R.2・R.3）。
 *
 * <p>{@code catch (Exception)} で一括りにすると、問い合わせ側が業務の判断で断ったことまで
 * {@code IllegalStateException} に化け、画面には 500 が出る。利用者は「壊れた」のか
 * 「入力が悪い」のかを判断できない（`architecture_backend.md`「例外と HTTP の対応」）。</p>
 *
 * <p>型では見分けない。サービスを越えると根の型が置き換わるので、文言の印で見る
 * （ADR-0001 決定 5 第 12 項。{@code ApiExceptionHandler} と同じ判断）。</p>
 */
class QueryDispatcherTest {

    private static QueryDispatcher failingWith(Throwable cause) {
        return new QueryDispatcher(new QueryDispatcher.Gateway() {
            @Override
            public <R> CompletableFuture<R> query(Object query, Class<R> responseType) {
                return CompletableFuture.failedFuture(cause);
            }
        });
    }

    private static QueryDispatcher answering(Object answer) {
        return new QueryDispatcher(new QueryDispatcher.Gateway() {
            @Override
            public <R> CompletableFuture<R> query(Object query, Class<R> responseType) {
                return CompletableFuture.completedFuture(responseType.cast(answer));
            }
        });
    }

    @Test
    @DisplayName("問い合わせの答えをそのまま返す")
    void returnsTheAnswer() {
        assertThat(answering("答え").query("問い合わせ", String.class)).isEqualTo("答え");
    }

    @Test
    @DisplayName("業務規則違反は 500 に化けない")
    void keepsBusinessRuleViolation() {
        QueryDispatcher dispatcher = failingWith(new BusinessRuleViolation("知らない貨物種別です"));

        assertThatThrownBy(() -> dispatcher.query("問い合わせ", String.class))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("知らない貨物種別です");
    }

    @Test
    @DisplayName("状態遷移違反は 500 に化けない")
    void keepsIllegalTransition() {
        QueryDispatcher dispatcher = failingWith(new IllegalTransition("まだ確定していません"));

        assertThatThrownBy(() -> dispatcher.query("問い合わせ", String.class))
                .isInstanceOf(IllegalTransition.class)
                .hasMessageContaining("まだ確定していません");
    }

    @Test
    @DisplayName("包みが何枚あっても印を見つける")
    void looksThroughEveryWrapper() {
        // サービスを越えると根の型は置き換わる。型で見ると、越えた瞬間に 409 が 500 になる。
        QueryDispatcher dispatcher = failingWith(new CompletionException(
                new IllegalStateException("問い合わせに失敗しました",
                        new RuntimeException(IllegalTransition.MARKER + "まだ確定していません"))));

        assertThatThrownBy(() -> dispatcher.query("問い合わせ", String.class))
                .isInstanceOf(IllegalTransition.class)
                .hasMessageContaining("まだ確定していません");
    }

    @Test
    @DisplayName("業務の判断でない失敗は 500 のまま")
    void stillFailsHardOnInfrastructureFailure() {
        QueryDispatcher dispatcher = failingWith(new RuntimeException("接続できません"));

        assertThatThrownBy(() -> dispatcher.query("問い合わせ", String.class))
                .as("障害を業務の断りに化けさせると、原因が残らない")
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(IllegalTransition.class)
                .hasMessageContaining("問い合わせに失敗しました");
    }

    @Test
    @DisplayName("待っているあいだに中断されたら割り込みを立て直す")
    void restoresInterruptFlag() {
        QueryDispatcher dispatcher = new QueryDispatcher(new QueryDispatcher.Gateway() {
            @Override
            public <R> CompletableFuture<R> query(Object query, Class<R> responseType) {
                Thread.currentThread().interrupt();
                return new CompletableFuture<>();
            }
        });

        assertThatThrownBy(() -> dispatcher.query("問い合わせ", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("中断されました");
        assertThat(Thread.interrupted()).as("割り込みを飲み込むと、上位が止まれない").isTrue();
    }
}
