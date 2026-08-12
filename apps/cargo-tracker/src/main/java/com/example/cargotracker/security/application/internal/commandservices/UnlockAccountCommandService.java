package com.example.cargotracker.security.application.internal.commandservices;

import com.example.cargotracker.security.domain.model.aggregates.UserAccount;
import com.example.cargotracker.security.domain.repository.UserAccountRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ロックされたアカウントの解除（US33）。 */
@Service
public class UnlockAccountCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.security");

    private final UserAccountRepository repository;

    public UnlockAccountCommandService(UserAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * ロックを解除する。
     *
     * <p><strong>理由は必須である。</strong> 誰がなぜ解除したかを追えないと、
     * 監査ログは「解除された」という事実しか残さない。それでは、
     * 不正な解除と正当な解除を後から区別できない。
     *
     * @param username 対象の利用者名
     * @param reason   解除の理由
     * @param actor    操作した管理者
     * @return 対象が存在しなければ空
     */
    @Transactional
    public Optional<UserAccount> unlock(String username, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("解除の理由は必須です");
        }
        Optional<UserAccount> found = repository.findByUsernameForUpdate(username);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserAccount account = found.get();
        account.unlock();
        repository.updateLockState(account);

        if (AUDIT.isInfoEnabled()) {
            // **理由をログに含める。** 事実だけでは、後から正当性を判断できない
            AUDIT.info("アカウントロック解除 username={} reason={} actor={}",
                    AuditValue.sanitize(username),
                    AuditValue.sanitize(reason),
                    AuditValue.sanitize(actor));
        }
        return Optional.of(account);
    }
}
