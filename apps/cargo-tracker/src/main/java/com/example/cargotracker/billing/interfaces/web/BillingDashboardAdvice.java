package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.queryservices.BillingQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードの「未請求の引取済貨物」カード（US21。ADR-014）。
 *
 * <p><strong>既存の 3 カードはいずれも請求書がある貨物を数えている</strong>
 * （未払い請求・支払期限超過・今月の請求総額）。<strong>まだ請求書が無い貨物に
 * 気づく手段が無かった。</strong>
 *
 * <p><strong>件数を出すだけでは仕事は進まない</strong>（IT9 のふりかえり T2）。
 * カードから請求対象一覧へ行ける。
 */
@ControllerAdvice(assignableTypes = com.example.cargotracker.shared.infrastructure.web
        .HomeController.class)
public class BillingDashboardAdvice {

    private final BillingQueryService queryService;

    public BillingDashboardAdvice(BillingQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 未請求の引取済貨物の件数。
     *
     * <p><strong>経理担当者にだけ数える。</strong> 他のロールには使われない数を
     * 毎回引かない。
     */
    @ModelAttribute("pendingBillingCount")
    public int pendingBillingCount(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_BILLING".equals(a.getAuthority()))) {
            return 0;
        }
        return queryService.countPendingCargo();
    }
}
