package com.example.cargotracker.security.infrastructure.config;

import com.example.cargotracker.security.domain.model.Role;
import jakarta.servlet.http.HttpServletResponse;
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
    // throws Exception は Spring Security が定めるシグネチャである（HttpSecurity#build）。
    // 狭めることはできない。
    @SuppressWarnings({"java:S112", "java:S1130"})
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
                // 貨物予約は**営業担当者のみ**とする。
                //
                // <strong>荷主に開放してはならない。</strong> 利用者アカウントと荷主を
                // 結びつける手段がまだ無く、荷主に一覧を見せると**他社の予約まで見える**。
                // non_functional.md は ROLE_SHIPPER を「自社予約・追跡（Phase 2）」と
                // 定めており、「自社の」を実現できない今、開放は正典に反する。
                // 荷主セルフサービスは利用者と荷主の紐付けを伴う別のストーリーで扱う。
                .requestMatchers("/bookings", "/bookings/**").hasRole(Role.SALES.name())
                // 航路管理と経路割り当て待ちは経路設計者のみ（ui_design.md）
                .requestMatchers("/voyages", "/voyages/**").hasRole(Role.ROUTER.name())
                .requestMatchers("/routing", "/routing/**").hasRole(Role.ROUTER.name())
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
            .exceptionHandling(ex -> ex
                // 既定の Whitelabel Error Page（英語・status=403）を見せない。
                // 利用者は障害だと受け取り、情シスへの問い合わせになる。
                // **forward にするのは、コンテナのエラーディスパッチに依存せず
                // どの実行経路でも同じ画面を出すため。**
                .accessDeniedHandler((request, response, denied) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    request.getRequestDispatcher("/access-denied").forward(request, response);
                }))
            .headers(headers -> headers
                // ログアウト後にブラウザバックで業務画面が見えないようにする（US27）
                .cacheControl(cache -> {}));
        return http.build();
    }
}
