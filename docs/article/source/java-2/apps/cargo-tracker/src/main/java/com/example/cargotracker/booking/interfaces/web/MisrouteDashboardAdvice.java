package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.shared.infrastructure.web.DashboardController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードに誤配の件数を載せる（US28。ADR-014 / IT12 の C34）。
 *
 * <p>ADR-014 の表が挙げていながら実装されていなかったカードである。
 * <strong>誤配は貨物が予定と違う港にある状態であり、気づくのが遅れるほど
 * 積み替えの選択肢が減る。</strong>
 *
 * <p><strong>件数を持つ BC が自分で載せる</strong>（ADR-014）。共有のダッシュボードが
 * 全 BC のクエリサービスを参照する形を避ける。
 *
 * <p><strong>見ないロールでは数えない。</strong> 全ロールで走らせると、
 * ログイン直後の 1 画面で使わない集計が並ぶ。
 */
@ControllerAdvice(assignableTypes = DashboardController.class)
public class MisrouteDashboardAdvice {

    private final BookingQueryService queryService;

    public MisrouteDashboardAdvice(BookingQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 誤配のまま経路が決まっていない予約の件数。
     *
     * <p>見るのは<strong>追跡管理者と営業担当者</strong>である。前者は例外として
     * 追い、後者は荷主に説明する。<strong>件数から対象の一覧へ直接行く</strong>
     * （カード側がリンクを持つ）。
     */
    @ModelAttribute("misroutedBookings")
    public int misroutedBookings(Authentication authentication) {
        return hasRole(authentication, "ROLE_TRACKER") || hasRole(authentication, "ROLE_SALES")
                ? queryService.countMisrouted()
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
