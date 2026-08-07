package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.commandservices.ProposeRoutesCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteProposalQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteProposalView;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 経路割り当ての画面（US08）。
 *
 * <p>アクセスできるのは ROLE_ROUTER のみ（{@code SecurityConfig}）。
 *
 * <p>URL は予約の下（{@code /bookings/{id}/route}）だが、<strong>扱うのは
 * Routing Context の関心事</strong>であるため本 BC に置く。予約の内容は
 * ACL ポート（{@code RoutableBookings}）経由で読む。
 */
@Controller
@RequestMapping("/bookings/{bookingId}/route")
public class RouteAssignmentController {

    private static final String VIEW = "routing/assignment";
    private static final String UNKNOWN_ACTOR = "unknown";

    private final RouteProposalQueryService queryService;
    private final ProposeRoutesCommandService proposeService;

    public RouteAssignmentController(
            RouteProposalQueryService queryService,
            ProposeRoutesCommandService proposeService) {
        this.queryService = queryService;
        this.proposeService = proposeService;
    }

    /** 経路割り当て画面。まだ算出していない予約も開ける。 */
    @GetMapping
    public String show(@PathVariable("bookingId") String bookingId, Model model) {
        RouteProposalView view = queryService.find(parse(bookingId))
                .orElseThrow(RouteAssignmentController::notFound);
        model.addAttribute("proposal", view);
        return VIEW;
    }

    /**
     * 経路候補を算出する（US08）。
     *
     * <p>算出のあとは PRG で画面へ戻す。<strong>再読み込みで再算出しない。</strong>
     */
    @PostMapping("/proposals")
    public String propose(@PathVariable("bookingId") String bookingId, Principal principal) {
        RoutingBookingId id = parse(bookingId);
        proposeService.propose(id, principal == null ? UNKNOWN_ACTOR : principal.getName())
                .orElseThrow(RouteAssignmentController::notFound);
        return "redirect:/bookings/" + id.value() + "/route";
    }

    /**
     * 予約 ID を読む。
     *
     * <p>形式が不正なら 404 にする。<strong>URL を直接編集しただけで 500 にしない。</strong>
     */
    private static RoutingBookingId parse(String bookingId) {
        try {
            return RoutingBookingId.of(bookingId);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "経路割り当ての対象となる予約が見つかりません");
    }
}
