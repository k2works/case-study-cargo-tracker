package com.example.authms.application.port;

import com.example.authms.domain.model.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    /** 認証の成否に伴う状態（失敗回数・ロック期限）を保存する。 */
    void updateLoginState(User user);
}
