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
}
