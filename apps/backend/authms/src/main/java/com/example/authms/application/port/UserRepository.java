package com.example.authms.application.port;

import com.example.authms.domain.model.User;
import java.time.Instant;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    /** 認証の成否に伴う状態（失敗回数・ロック期限）を保存する。 */
    void updateLoginState(User user);

    /**
     * ログイン失敗を 1 回数える。同時に届いた試行を取りこぼさないことを実装側で保証する。
     *
     * @param user 失敗した利用者（読み取った時点の状態）
     * @param now 現在時刻
     * @return 数え上げた後の状態。ロックが成立したかはこの戻り値で判断する
     */
    User recordFailedAttempt(User user, Instant now);

    /**
     * いまロックされている利用者を返す（US32-1）。
     *
     * <p><strong>期限切れは含めない。</strong>期限が切れたロックは解除操作なしで受け付けが
     * 戻っており、一覧に出すと管理者は要らない作業をする。
     *
     * @param now 現在時刻。<strong>注入した Clock から渡す</strong>——ここで
     *     {@code Instant.now()} を呼ぶと、テストと実装で別の「いま」を見ることになる
     */
    java.util.List<User> findLocked(Instant now);
}
