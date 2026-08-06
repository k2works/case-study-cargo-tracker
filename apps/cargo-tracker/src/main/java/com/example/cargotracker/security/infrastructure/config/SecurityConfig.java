package com.example.cargotracker.security.infrastructure.config;

import com.example.cargotracker.security.domain.model.Role;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証・認可の構成。
 *
 * <p>ロールと画面の対応の正典は {@code docs/design/non_functional.md} の RBAC ロール定義と
 * {@code docs/design/ui_design.md} の画面一覧である。ここに独自の対応を作らない。
 */
@Configuration
public class SecurityConfig {

    /** BCrypt のコスト。{@code non_functional.md} が正典。 */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // ヘルスチェックは横断的な防御の対象外にする。
                // 過負荷時に liveness が 401/503 を返すと ECS が再起動ループに入る。
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // 公開追跡は認証不要（US18）。個人情報は返さない。
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**", "/error").permitAll()
                // 荷主管理は営業担当者のみ（ui_design.md のナビゲーション構成）
                .requestMatchers("/shippers", "/shippers/**").hasRole(Role.SALES.name())
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .headers(headers -> headers
                // ログアウト後にブラウザバックで業務画面が見えないようにする（US27）
                .cacheControl(cache -> {}));
        return http.build();
    }
}
