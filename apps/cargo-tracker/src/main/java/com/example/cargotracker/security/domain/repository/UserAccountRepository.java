package com.example.cargotracker.security.domain.repository;

import com.example.cargotracker.security.domain.model.UserAccount;
import java.util.Optional;

/** 認証アカウントの出力ポート。実装はインフラ層に置く（DIP）。 */
public interface UserAccountRepository {

    Optional<UserAccount> findByUsername(String username);

    /** 失敗回数とロック期限を永続化する。 */
    void updateLockState(UserAccount account);
}
