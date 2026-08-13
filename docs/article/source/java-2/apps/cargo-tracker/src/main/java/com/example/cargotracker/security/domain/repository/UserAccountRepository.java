package com.example.cargotracker.security.domain.repository;

import com.example.cargotracker.security.domain.model.aggregates.UserAccount;
import java.util.Optional;

/** 認証アカウントの出力ポート。実装はインフラ層に置く（DIP）。 */
public interface UserAccountRepository {

    Optional<UserAccount> findByUsername(String username);

    /**
     * 失敗回数を更新する目的で、行を排他的に取得する。
     *
     * <p><strong>認証失敗の記録は読み込み・加算・書き込みであり、そのままでは原子的でない。</strong>
     * 総当たり攻撃は逐次では来ないため、並行した失敗が同じ値を読んで同じ値を書くと
     * 回数が上限に届かず、ロックが成立しない。同一利用者への更新をここで直列化する。
     *
     * <p>不変条件（何回でロックするか・期限切れで数え直すか）は {@link UserAccount} が持つ。
     * SQL に条件分岐を書き写すと、不変条件がドメインと DB の 2 箇所に分かれる。
     *
     * <p>呼び出し側はトランザクション内で使うこと。
     */
    Optional<UserAccount> findByUsernameForUpdate(String username);

    /** 失敗回数とロック期限を永続化する。 */
    void updateLockState(UserAccount account);
}
