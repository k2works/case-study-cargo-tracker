package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.commandservices.ProposeRoutesCommandService;
import com.example.cargotracker.routing.application.internal.commandservices.SelectRouteCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteProposalQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteProposalView;
import com.example.cargotracker.routing.domain.model.valueobjects.RelaxationRequest;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageNumber;
import java.security.Principal;
import java.util.ConcurrentModificationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private static final String FLASH_ERROR = "flashError";
    private static final String REDIRECT_BOOKING = "redirect:/bookings/";
    private static final String ROUTE_PATH = "/route";

    private final RouteProposalQueryService queryService;
    private final ProposeRoutesCommandService proposeService;
    private final SelectRouteCommandService selectService;

    public RouteAssignmentController(
            RouteProposalQueryService queryService,
            ProposeRoutesCommandService proposeService,
            SelectRouteCommandService selectService) {
        this.queryService = queryService;
        this.proposeService = proposeService;
        this.selectService = selectService;
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
    public String propose(
            @PathVariable("bookingId") String bookingId,
            @RequestParam(name = "extraDays", defaultValue = "0") int extraDays,
            @RequestParam(name = "maxTransitCount", required = false) Integer maxTransitCount,
            Principal principal,
            RedirectAttributes redirect) {
        RoutingBookingId id = parse(bookingId);

        RelaxationRequest relaxation;
        try {
            relaxation = new RelaxationRequest(extraDays, maxTransitCount);
        } catch (IllegalArgumentException e) {
            // **緩め方の上限を超えた。** 切り詰めて算出せず、理由をそのまま返す
            // （要求と違う条件で探した結果を、要求どおりの結果として見せない）
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_BOOKING + id.value() + ROUTE_PATH;
        }

        try {
            proposeService.propose(
                            id, relaxation,
                            principal == null ? UNKNOWN_ACTOR : principal.getName())
                    .orElseThrow(RouteAssignmentController::notFound);
        } catch (IllegalArgumentException | ConcurrentModificationException e) {
            // 累積の上限超過（1 回分の検査は上で済んでいる）と、
            // 別の担当者が先に算出していた場合。**どちらも 500 にしない。**
            // 何が起きたかと、次にどうすればよいかをそのまま伝える
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return REDIRECT_BOOKING + id.value() + ROUTE_PATH;
    }

    /**
     * 経路を確定して予約に紐付ける（US09 / US11）。
     *
     * <p>確定できたら<strong>予約詳細へ</strong>戻す。確定した経路はそこで読める。
     * 確定できなかったら経路割り当て画面に留まり、理由を示す。
     */
    @PostMapping("/selection")
    public String select(
            @PathVariable("bookingId") String bookingId,
            @RequestParam("voyageNumber") String voyageNumber,
            Principal principal,
            RedirectAttributes redirect) {

        RoutingBookingId id = parse(bookingId);
        SelectRouteCommandService.Result result;
        try {
            result = selectService.select(id, new VoyageNumber(voyageNumber),
                    principal == null ? UNKNOWN_ACTOR : principal.getName());
        } catch (ConcurrentModificationException e) {
            // 提案の側で衝突した。**算出のときと同じ扱いにする。**
            // 片方だけ 500 になると、利用者から見て振る舞いが揃わない
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_BOOKING + id.value() + ROUTE_PATH;
        }

        switch (result.outcome()) {
            case NOT_FOUND -> throw notFound();
            case REJECTED -> {
                // **選べない理由をそのまま返す。** 「確定できません」だけでは直せない
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                return REDIRECT_BOOKING + id.value() + ROUTE_PATH;
            }
            case CONFLICTED -> {
                redirect.addFlashAttribute(FLASH_ERROR,
                        "別の担当者が先に更新しました。最新の内容を確認してください");
                return REDIRECT_BOOKING + id.value() + ROUTE_PATH;
            }
            default -> { /* 確定できたので下へ進む */ }
        }

        redirect.addFlashAttribute("flashSuccess",
                "経路 " + voyageNumber + " を割り当てました");
        return REDIRECT_BOOKING + id.value();
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
