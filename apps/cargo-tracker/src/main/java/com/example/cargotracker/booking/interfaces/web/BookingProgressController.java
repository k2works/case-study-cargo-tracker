package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.AssignToRoutingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.CancelBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ConfirmBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.IssueTrackingNumberCommandService;
import com.example.cargotracker.booking.domain.model.BookingId;
import java.security.Principal;
import java.util.ConcurrentModificationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 予約の状態を進める操作（US06 / US13 / US14 と、キャンセル）。
 *
 * <p>遷移の正典は {@code domain-model.md}「BookingStatus 状態遷移表」である。
 * <strong>ここに置くのは遷移そのものだけ</strong>であり、可否の判断は集約が持つ。
 *
 * <p>操作ごとに実行するロールが異なる（引き渡し・確定・キャンセルは営業担当者、
 * 追跡番号の発行は追跡管理者）。認可の規則は {@code SecurityConfig} が
 * URL ごとに定める。<strong>画面のボタン出し分けは集約の述語をそのまま呼ぶ</strong>
 * ため、規則を画面に書き写さない。
 */
@Controller
@RequestMapping("/bookings")
public class BookingProgressController {

    private static final String REDIRECT_DETAIL = "redirect:/bookings/";
    private static final String FLASH_ERROR = "flashError";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String UNKNOWN_ACTOR = "unknown";
    private static final String NOT_FOUND_MESSAGE = "予約が見つかりません";

    /**
     * 同時操作で先を越されたときの文言。
     *
     * <p><strong>操作ごとに書き分けない。</strong> 利用者から見て起きたことは同じであり、
     * 文言が揺れると「別の障害では」と受け取られる。
     */
    private static final String CONFLICT_MESSAGE =
            "他の操作が先に行われました。最新の内容を確認してください";

    private final AssignToRoutingCommandService assignService;
    private final ConfirmBookingCommandService confirmService;
    private final IssueTrackingNumberCommandService issueTrackingNumberService;
    private final CancelBookingCommandService cancelService;

    public BookingProgressController(
            AssignToRoutingCommandService assignService,
            ConfirmBookingCommandService confirmService,
            IssueTrackingNumberCommandService issueTrackingNumberService,
            CancelBookingCommandService cancelService) {
        this.assignService = assignService;
        this.confirmService = confirmService;
        this.issueTrackingNumberService = issueTrackingNumberService;
        this.cancelService = cancelService;
    }

    /** 経路設計者に引き渡す（US06。遷移表 #2）。 */
    @PostMapping("/{bookingId}/assign-to-routing")
    public String assignToRouting(
            @PathVariable String bookingId, Principal principal, RedirectAttributes redirect) {

        BookingId id = parseBookingId(bookingId);
        var outcome = assignService.assign(
                id, principal == null ? UNKNOWN_ACTOR : principal.getName());

        switch (outcome) {
            case ASSIGNED -> redirect.addFlashAttribute(
                    FLASH_SUCCESS, "経路設計者に引き渡しました。経路割り当て待ち一覧に表示されます");
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case NOT_ASSIGNABLE -> redirect.addFlashAttribute(
                    FLASH_ERROR, "この状態の予約は引き渡せません");
            default -> redirect.addFlashAttribute(
                    FLASH_ERROR, CONFLICT_MESSAGE);
        }
        return REDIRECT_DETAIL + bookingId;
    }

    @PostMapping("/{bookingId}/cancel")
    public String cancel(
            @PathVariable String bookingId, Principal principal, RedirectAttributes redirect) {

        BookingId id = parseBookingId(bookingId);

        var outcome = cancelService.cancel(id, principal == null ? UNKNOWN_ACTOR : principal.getName());
        switch (outcome) {
            case CANCELLED -> redirect.addFlashAttribute(FLASH_SUCCESS, "予約をキャンセルしました");
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case NOT_CANCELLABLE -> redirect.addFlashAttribute(
                    FLASH_ERROR, "この状態の予約はキャンセルできません");
            default -> redirect.addFlashAttribute(
                    FLASH_ERROR, CONFLICT_MESSAGE);
        }
        return REDIRECT_DETAIL + bookingId;
    }


    /**
     * 予約を確定する（US13。遷移表 #4）。
     *
     * <p>確定した予約は追跡番号発行待ち一覧に現れる。<strong>通知は送らない</strong>
     * （ADR-006）。待ち行列に現れることが業務上の「発行依頼」である。
     */
    @PostMapping("/{bookingId}/confirm")
    public String confirm(
            @PathVariable String bookingId, Principal principal, RedirectAttributes redirect) {

        BookingId id = parseBookingId(bookingId);
        var result = confirmService.confirm(
                id, principal == null ? UNKNOWN_ACTOR : principal.getName());

        switch (result.outcome()) {
            case CONFIRMED -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "予約を確定しました。追跡番号の発行を待ちます");
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            // **理由をそのまま返す。** 満船なのか経路が無いのかで、次の操作が変わる
            case REJECTED -> redirect.addFlashAttribute(FLASH_ERROR, result.reason());
            default -> redirect.addFlashAttribute(
                    FLASH_ERROR, CONFLICT_MESSAGE);
        }
        return REDIRECT_DETAIL + bookingId;
    }

    /**
     * 追跡番号を発行する（US14。遷移表 #5）。
     *
     * <p><strong>メール通知は送らない</strong>（ADR-006）。発行した番号は
     * 予約詳細に表示する。
     */
    @PostMapping("/{bookingId}/tracking-number")
    public String issueTrackingNumber(
            @PathVariable String bookingId, Principal principal, RedirectAttributes redirect) {

        BookingId id = parseBookingId(bookingId);
        IssueTrackingNumberCommandService.Result result;
        try {
            result = issueTrackingNumberService.issue(
                    id, principal == null ? UNKNOWN_ACTOR : principal.getName());
        } catch (ConcurrentModificationException e) {
            // 発行の途中で衝突した。**500 にしない。**
            // 何が起きたかと、次にどうすればよいかを伝える
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_DETAIL + bookingId;
        }

        switch (result.outcome()) {
            case ISSUED -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "追跡番号 " + result.trackingNumber() + " を発行しました");
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED -> redirect.addFlashAttribute(FLASH_ERROR, result.reason());
            default -> redirect.addFlashAttribute(
                    FLASH_ERROR, CONFLICT_MESSAGE);
        }
        return REDIRECT_DETAIL + bookingId;
    }

    /** URL を直接編集しただけで 500 にしない。 */
    private static BookingId parseBookingId(String bookingId) {
        try {
            return BookingId.of(bookingId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
    }
}
