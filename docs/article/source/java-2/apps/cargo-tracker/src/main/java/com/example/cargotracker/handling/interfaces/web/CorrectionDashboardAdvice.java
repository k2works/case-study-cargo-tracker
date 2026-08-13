package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.queryservices
        .CorrectionQueryService;
import com.example.cargotracker.shared.infrastructure.web.DashboardController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードに訂正・取り消しの承認待ち件数を載せる（US36。ADR-014）。
 *
 * <p><strong>申請しただけでは貨物の状態は戻らない。</strong> 承認されるまで、
 * 届いていない貨物が配送完了のまま残る。
 *
 * <p><strong>件数を持つ BC が自分で載せる</strong>（ADR-014）。
 * <strong>見ないロールでは数えない。</strong>
 */
@ControllerAdvice(assignableTypes = DashboardController.class)
public class CorrectionDashboardAdvice {

    private final CorrectionQueryService queryService;

    public CorrectionDashboardAdvice(CorrectionQueryService queryService) {
        this.queryService = queryService;
    }

    /** 承認待ちの件数。**数えた対象にそのまま行ける**（カード側がリンクを持つ）。 */
    @ModelAttribute("pendingCorrections")
    public int pendingCorrections(Authentication authentication) {
        return hasRole(authentication, "ROLE_TRACKER") || hasRole(authentication, "ROLE_HANDLER")
                ? queryService.countPending()
                : 0;
    }

    private static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
