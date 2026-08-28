package com.example.authms.application.internal.commandservices;

import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.AuthEventType;
import com.example.authms.domain.model.User;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 管理者がロックを解除する（US32）。
 *
 * <p>解除は<strong>必ず記録する</strong>（US32-3）。画面には認証の失敗理由を出さないため
 * （US31）、何が起きたかを追える場所は監査ログだけである。そこに「誰が解除したか」が
 * 無いと、あとから誰にも説明できない。
 */
@Service
public class UnlockAccountUseCase {

    private final UserRepository users;
    private final AuthAuditLogger audit;
    private final Clock clock;

    public UnlockAccountUseCase(UserRepository users, AuthAuditLogger audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * いまロックされている利用者（US32-1）。
     *
     * <p>時刻は注入した {@link Clock} から取る。ここで {@code Instant.now()} を呼ぶと、
     * テストと実装で別の「いま」を見ることになる。
     */
    public List<User> lockedAccounts() {
        return users.findLocked(clock.instant());
    }

    /**
     * 解除する。対象がいなければ空を返す。
     *
     * @param actor 解除した管理者の利用者 ID。<strong>記録に残すのはこれである</strong>
     */
    public Optional<User> unlock(String username, String actor) {
        return users.findByUsername(username).map(user -> {
            User unlocked = user.unlock();
            users.updateLoginState(unlocked);
            // 解除できたことと、記録できたことは別。記録は必ず通す
            audit.log(username, AuthEventType.UNLOCKED, "管理者による解除", actor);
            return unlocked;
        });
    }
}
