package com.example.cargotracker.security.infrastructure.config;

import com.example.cargotracker.security.domain.model.Role;
import com.example.cargotracker.security.domain.model.UserAccount;
import com.example.cargotracker.security.domain.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 認証アカウントを Spring Security に橋渡しする。
 *
 * <p>ロック状態の判定はドメイン（{@link UserAccount#isLockedAt}）に委ねる。
 * ここで期限の比較を書くと、判定がドメインとインフラの 2 箇所に散る。
 */
@Service
public class CargoTrackerUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;
    private final Clock clock;

    public CargoTrackerUserDetailsService(UserAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = repository.findByUsername(username)
                // 存在しない利用者と認証情報の誤りを区別させない（US31）。
                // メッセージの出し分けはここではなく認証失敗ハンドラで一本化する。
                .orElseThrow(() -> new UsernameNotFoundException("認証に失敗しました"));

        List<SimpleGrantedAuthority> authorities = account.roles().stream()
                .map(Role::authority)
                .map(SimpleGrantedAuthority::new)
                .toList();

        return User.withUsername(account.username())
                .password(account.passwordHash())
                .authorities(authorities)
                .disabled(!account.enabled())
                // ロック中は正しいパスワードでも認証を通さない（US31 の受入基準）
                .accountLocked(account.isLockedAt(clock.instant()))
                .build();
    }
}
