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
                // **順序が要である。** /bookings/new は下の /bookings/* にも一致するため、
                // 先に営業担当者限定として宣言する。**規則を後ろに書くと効かない**
                .requestMatchers("/bookings/new").hasRole(Role.SALES.name())
                // 通知待ちの一覧（US12 / IT8）は営業担当者のみ。
                // **/bookings/* にも一致するため、ここで先に宣言する。**
                // 後ろに書くと経路設計者・追跡管理者にも見えてしまう
                //（IT5・IT7 で規則の順序に当たったのと同じ形である）
                .requestMatchers("/bookings/notification-queue").hasRole(Role.SALES.name())
                // 経路割り当て（US08）は経路設計者のみ。**GET も POST も同じ**。
                // 読めるだけで算出を実行できないと、画面が使えない。
                // ここも /bookings/** より前に置く（後ろに書くと効かない）
                .requestMatchers("/bookings/*/route", "/bookings/*/route/**")
                        .hasRole(Role.ROUTER.name())
                // 追跡番号の発行（US14）は**追跡管理者のみ**である（遷移表 #5）。
                // /bookings/** より前に置く（後ろに書くと効かない）
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/bookings/*/tracking-number")
                        .hasRole(Role.TRACKER.name())
                // **予約詳細は経路設計者と追跡管理者も開ける。** 引き渡された予約の内容を
                // 確認できないと経路を選べず、追跡管理者は発行の対象を確かめられない。
                // **押す人が画面を開けない状態にしない**（IT6 開始準備の突合で発覚）。
                // 登録・キャンセル・引き渡し・確定は POST の規則により営業担当者のみである
                // **登録フォームを先に営業へ限定する。** `/bookings/*` は
                // `/bookings/{予約 ID}` だけでなく `/bookings/new` にも一致する。
                // 開けてしまうと、荷主は全項目を入力したあとで 403 に当たる
                // （送信の POST は営業のみのため）。**押せない操作を見せない。**
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/bookings/new", "/bookings/new/**")
                        .hasRole(Role.SALES.name())
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/bookings/*")
                        .hasAnyRole(Role.SALES.name(), Role.ROUTER.name(),
                                Role.TRACKER.name(), Role.SHIPPER.name())
                // 予約一覧を荷主に開く（US34 / IT9）。**IT2 で一度開いて取り消した場所である。**
                // 当時は利用者と荷主を結びつける手段が無く、他社の予約まで見えていた。
                // いまは紐付けがあり、**絞り込みは SQL で行う**（画面側で捨てない）。
                // GET だけを開く。登録・キャンセル・引き渡し・確定は下の規則で営業のみ
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/bookings")
                        .hasAnyRole(Role.SALES.name(), Role.SHIPPER.name())
                .requestMatchers("/bookings", "/bookings/**").hasRole(Role.SALES.name())
                // 航路管理と経路割り当て待ちは経路設計者のみ（ui_design.md）
                .requestMatchers("/voyages", "/voyages/**").hasRole(Role.ROUTER.name())
                .requestMatchers("/routing", "/routing/**").hasRole(Role.ROUTER.name())
                // **追跡は 2 種類の画面が同じ接頭辞を共有する。** 順序が要である。
                //
                // 1. 追跡管理者の作業用（発行待ち一覧・例外管理・手動更新）
                // 2. 荷主・荷受人が自分の貨物を照会する画面（US18）
                //
                // 作業用を**先に**宣言する。後ろに書くと下の 3 ロール規則に飲み込まれ、
                // **他社を含む確定済み予約が並ぶ発行待ち一覧が荷主に見える**。
                // IT5 で規則の順序に一度当たっており、同じ形である。
                .requestMatchers("/tracking/queue", "/tracking/queue/**")
                        .hasRole(Role.TRACKER.name())
                // **エスカレーション中の一覧は管理者が見る**（US20 / IT10）。
                // 「管理職への escalation 通知」の受け皿であり、**送っただけで
                // 誰も見ないなら意味が無い**。
                //
                // /tracking/exceptions/** より**前**に置く。後ろに書くと
                // 追跡管理者限定の規則に飲み込まれ、管理者が 403 に当たる
                // （IT5・IT7 で規則の順序に当たったのと同じ形である）
                .requestMatchers("/tracking/exceptions/escalated")
                        .hasRole(Role.ADMIN.name())
                .requestMatchers("/tracking/exceptions", "/tracking/exceptions/**")
                        .hasRole(Role.TRACKER.name())
                // 状態の手動更新（US17 / IT8）は追跡管理者のみ
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/tracking/*/status")
                        .hasRole(Role.TRACKER.name())
                // 追跡照会（US18）。**追跡番号そのものが合鍵である。**
                // 一覧は出さないため、番号を知らない利用者は何も引き当てられない。
                // 荷主に「自社の貨物の一覧」を出すのは利用者と荷主の紐付けが
                // できてから（US34 / IT9）である。
                .requestMatchers("/tracking", "/tracking/**")
                        .hasAnyRole(Role.SHIPPER.name(), Role.CONSIGNEE.name(),
                                Role.TRACKER.name())
                // 荷役（US15）は荷役作業員のみ。**現場が使う唯一の画面である**
                .requestMatchers("/handling", "/handling/**").hasRole(Role.HANDLER.name())
                // 管理（ロック解除。US33）は管理者のみ。**GET も POST も同じ**
                .requestMatchers("/admin", "/admin/**").hasRole(Role.ADMIN.name())
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
