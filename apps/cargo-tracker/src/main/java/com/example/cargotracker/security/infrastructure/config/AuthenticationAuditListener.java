package com.example.cargotracker.security.infrastructure.config;

import com.example.cargotracker.security.domain.model.aggregates.UserAccount;
import com.example.cargotracker.security.domain.repository.UserAccountRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * 認証の成否を記録し、連続失敗でアカウントをロックする（US26 / US31）。
 *
 * <p>ロックの判定と回数の管理は {@link UserAccount} が持つ。ここは永続化と
 * 監査ログへの記録に徹する。
 *
 * <p><strong>ログにパスワードを出さない。</strong> 失敗イベントは資格情報を保持しうるため、
 * 記録するのは利用者名と結果のみとする。
 */
@Component
public class AuthenticationAuditListener {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.authentication");

    private final UserAccountRepository repository;
    private final Clock clock;

    public AuthenticationAuditListener(UserAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        AUDIT.info("認証成功 username={}", username);
        repository.findByUsername(username).ifPresent(account -> {
            if (account.failedAttempts() > 0 || account.lockedUntil() != null) {
                account.recordSuccess();
                repository.updateLockState(account);
            }
        });
    }

    /**
     * 認証失敗を記録する。
     *
     * <p><strong>行を排他取得してから更新する。</strong> 読み込み・加算・書き込みは原子的でなく、
     * 並行した失敗が同じ値を読むと回数が上限に届かずロックが成立しない。
     * 総当たり攻撃は逐次では来ないため、ここを直列化しないとロックは働かない。
     */
    @org.springframework.transaction.annotation.Transactional
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = String.valueOf(event.getAuthentication().getName());
        AUDIT.info("認証失敗 username={} reason={}",
                username, event.getException().getClass().getSimpleName());
        repository.findByUsernameForUpdate(username).ifPresent(account -> {
            // ロック中の試行では失敗回数をさらに増やさない。
            // 増やし続けるとロック期限が実質的に延び続ける。
            if (account.isLockedAt(clock.instant())) {
                return;
            }
            account.recordFailure(clock.instant());
            repository.updateLockState(account);
            if (account.isLockedAt(clock.instant())) {
                AUDIT.warn("アカウントをロックしました username={} until={}",
                        username, account.lockedUntil());
            }
        });
    }
}
