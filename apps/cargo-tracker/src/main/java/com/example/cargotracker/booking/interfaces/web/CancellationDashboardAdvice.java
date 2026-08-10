package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices
        .CancellationQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードの「キャンセル承認待ち」カード（US30。ADR-014）。
 *
 * <p><strong>申請しただけでは仕事は終わらない。</strong> 承認するまで輸送は続くため、
 * 放置すると<strong>荷主が運びたくない貨物を運び続ける</strong>。
 *
 * <p><strong>数えるだけで終わらせない</strong>（IT9 のふりかえり T2）。
 * カードから承認待ち一覧へ行ける。
 *
 * <p><strong>対象は {@code DashboardController} である</strong>（IT13 で取り違えた）。
 * {@code HomeController} を指定すると件数が空欄のまま「件」だけが並ぶ。
 */
@ControllerAdvice(assignableTypes = com.example.cargotracker.shared.infrastructure.web
        .DashboardController.class)
public class CancellationDashboardAdvice {

    private final CancellationQueryService queryService;

    public CancellationDashboardAdvice(CancellationQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 決着していないキャンセル申請の件数。
     *
     * <p><strong>追跡管理者にだけ数える。</strong> 他のロールには使われない数を
     * 毎回引かない。
     */
    @ModelAttribute("pendingCancellationCount")
    public int pendingCancellationCount(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_TRACKER".equals(a.getAuthority()))) {
            return 0;
        }
        return queryService.countPending();
    }
}
