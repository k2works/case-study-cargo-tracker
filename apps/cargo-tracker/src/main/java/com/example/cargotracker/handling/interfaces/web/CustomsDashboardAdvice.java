package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.queryservices.CustomsQueryService;
import com.example.cargotracker.shared.infrastructure.web.DashboardController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードに通関の留置件数を載せる（US29。ADR-014）。
 *
 * <p><strong>件数を持つ BC が自分で載せる。</strong> 共有のダッシュボードが
 * 全 BC のクエリサービスを参照する形を避ける（ADR-014）。
 *
 * <p><strong>見ないロールでは数えない。</strong> 全ロールで走らせると、
 * ログイン直後の 1 画面で使わない集計が並ぶ（IT10 のレビュー指摘）。
 */
@ControllerAdvice(assignableTypes = DashboardController.class)
public class CustomsDashboardAdvice {

    private final CustomsQueryService queryService;

    public CustomsDashboardAdvice(CustomsQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 留置が長引いている申告の件数。
     *
     * <p><strong>放置すると保管料が発生する。</strong> 気づく手段を置き、
     * そこから対象の一覧へ行けるようにする（カード側がリンクを持つ）。
     */
    @ModelAttribute("customsHeldTooLong")
    public int customsHeldTooLong(Authentication authentication) {
        return hasRole(authentication, "ROLE_TRACKER") || hasRole(authentication, "ROLE_HANDLER")
                ? queryService.countHeldTooLong()
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
