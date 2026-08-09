package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.infrastructure.web.DashboardController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import com.example.cargotracker.tracking.application.internal.queryservices
        .TrackingExceptionQueryService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードに例外の件数を載せる（US19 / US20）。
 *
 * <p><strong>{@code shared} のダッシュボードから Tracking を呼ばない。</strong>
 * 呼ぶと {@code shared} が全 BC のクエリサービスを参照することになり、
 * カードが増えるたびに共有の画面が太る（ArchUnit ルール 4）。
 * 件数を載せたい BC が自分で載せる。
 *
 * <p><strong>対象をダッシュボードに限る。</strong> 限らないと、すべての画面で
 * 例外の件数を数える問い合わせが走る。
 */
@ControllerAdvice(assignableTypes = DashboardController.class)
public class TrackingExceptionDashboardAdvice {

    private final TrackingExceptionQueryService queryService;

    public TrackingExceptionDashboardAdvice(TrackingExceptionQueryService queryService) {
        this.queryService = queryService;
    }

    /** 未解決の例外の件数（追跡管理者のカード）。 */
    @ModelAttribute("unresolvedExceptions")
    public int unresolvedExceptions(Authentication authentication) {
        return hasRole(authentication, "ROLE_TRACKER") ? queryService.countUnresolved(false) : 0;
    }

    /** エスカレーション中の件数（管理者のカード）。 */
    @ModelAttribute("escalatingExceptions")
    public int escalatingExceptions(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN") ? queryService.countUnresolved(true) : 0;
    }

    private static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
