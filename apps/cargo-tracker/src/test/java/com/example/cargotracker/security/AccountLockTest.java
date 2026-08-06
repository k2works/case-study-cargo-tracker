package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.cargotracker.security.domain.repository.UserAccountRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * US31: 認証失敗が続いたアカウントを保護する。
 *
 * <p>ロック状態は<strong>リクエストをまたいで</strong>成立しなければならない。
 * 単一リクエスト内で完結するテストだけでは、永続化の漏れを検出できない。
 */
@AutoConfigureMockMvc
class AccountLockTest extends PostgreSQLIntegrationTestBase {

    private static final String TARGET = "billing";

    @Autowired
    private UserAccountRepository repository;

    @AfterEach
    void unlock() {
        // コミットを伴うためロールバックに頼れない。明示的に元へ戻す。
        repository.findByUsername(TARGET).ifPresent(a -> {
            a.unlock();
            repository.updateLockState(a);
        });
    }

    private void failLogin(int times) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(formLogin("/login").user(TARGET).password("wrong"))
                    .andExpect(unauthenticated());
        }
    }

    @Test
    void 認証失敗が4回まではロックされない() throws Exception {
        failLogin(4);

        assertThat(repository.findByUsername(TARGET).orElseThrow().failedAttempts()).isEqualTo(4);
        mockMvc.perform(formLogin("/login").user(TARGET).password("password"))
                .andExpect(authenticated());
    }

    @Test
    void 認証失敗が5回連続するとロックされる() throws Exception {
        failLogin(5);

        var account = repository.findByUsername(TARGET).orElseThrow();
        assertThat(account.failedAttempts()).isEqualTo(5);
        assertThat(account.lockedUntil()).isNotNull();
    }

    @Test
    void ロック中は正しいパスワードでもログインできない() throws Exception {
        failLogin(5);

        // ここが本ストーリーの核心。ロックが「入っている」ことではなく「働く」ことを確認する。
        mockMvc.perform(formLogin("/login").user(TARGET).password("password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 認証成功で失敗回数がリセットされる() throws Exception {
        failLogin(3);
        assertThat(repository.findByUsername(TARGET).orElseThrow().failedAttempts()).isEqualTo(3);

        mockMvc.perform(formLogin("/login").user(TARGET).password("password"))
                .andExpect(authenticated());

        assertThat(repository.findByUsername(TARGET).orElseThrow().failedAttempts()).isZero();
    }

    @Test
    void ロック中の試行では失敗回数がさらに増えない() throws Exception {
        failLogin(5);
        var locked = repository.findByUsername(TARGET).orElseThrow().lockedUntil();

        failLogin(3);

        var after = repository.findByUsername(TARGET).orElseThrow();
        assertThat(after.failedAttempts()).isEqualTo(5);
        // 増え続けるとロック期限が実質的に延び続ける
        assertThat(after.lockedUntil()).isEqualTo(locked);
    }

    @Test
    void 無効化されたアカウントはログインできない() throws Exception {
        mockMvc.perform(formLogin("/login").user("disabled").password("password"))
                .andExpect(unauthenticated())
                // ロック中・認証情報の誤り・無効化で同一の遷移先にする。
                // 出し分けるとアカウントの状態を攻撃者に教えることになる。
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 並行した認証失敗でもロックが成立する() throws InterruptedException {
        // **総当たり攻撃は逐次では来ない。** 読み込み・加算・書き込みが非原子だと、
        // 全スレッドが同じ値を読んで同じ値を書き、回数が上限に届かずロックが成立しない。
        // ロックを「入れたこと」ではなく「働くこと」で確かめる
        int concurrency = 5;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            for (int i = 0; i < concurrency; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    mockMvc.perform(formLogin("/login").user(TARGET).password("wrong"));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException | TimeoutException e) {
                    throw new AssertionError("並行した認証試行が完了しませんでした", e);
                }
            }
        }

        var account = repository.findByUsername(TARGET).orElseThrow();
        assertThat(account.failedAttempts())
                .as("並行実行でも失敗回数を取りこぼさない")
                .isGreaterThanOrEqualTo(5);
        assertThat(account.lockedUntil()).as("ロックが成立していること").isNotNull();
    }
}
