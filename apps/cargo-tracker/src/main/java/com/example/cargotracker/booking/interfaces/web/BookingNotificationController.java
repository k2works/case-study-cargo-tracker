package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.NotificationContentAssembler;
import com.example.cargotracker.booking.application.internal.commandservices.NotifyRouteCommandService;
import com.example.cargotracker.booking.application.internal.commandservices
        .ResendClaimCodeCommandService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.BookingId;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 荷主への経路通知（US12）。
 *
 * <p>アクセスできるのは ROLE_SALES のみ（{@code SecurityConfig} の {@code /bookings/**}）。
 * <strong>荷主とのやり取りは営業の仕事である。</strong>
 *
 * <p>ADR-006 により<strong>外部へは送らない</strong>。この画面が作るのは記録であり、
 * 「送ったつもり」を後から検知できるようにすることが目的である。
 */
@Controller
@RequestMapping("/bookings/{bookingId}/notifications")
public class BookingNotificationController {

    private static final String NOT_FOUND_MESSAGE = "予約が見つかりません";
    private static final String REDIRECT_DETAIL = "redirect:/bookings/";

    private final BookingQueryService queryService;
    private final NotificationContentAssembler contentAssembler;
    private final NotifyRouteCommandService notifyService;
    private final ResendClaimCodeCommandService resendClaimCodeService;

    public BookingNotificationController(
            BookingQueryService queryService,
            NotificationContentAssembler contentAssembler,
            NotifyRouteCommandService notifyService,
            ResendClaimCodeCommandService resendClaimCodeService) {
        this.queryService = queryService;
        this.contentAssembler = contentAssembler;
        this.notifyService = notifyService;
        this.resendClaimCodeService = resendClaimCodeService;
    }

    /**
     * 通知のプレビュー。
     *
     * <p><strong>送る前に内容と宛先を確認する。</strong> 宛先を確かめずに送ると、
     * 「送ったのに届いていない」の原因を後から切り分けられない。
     */
    @GetMapping("/new")
    public String preview(@PathVariable("bookingId") String bookingId, Model model) {
        var booking = queryService.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        model.addAttribute("booking", booking);
        try {
            model.addAttribute("content", contentAssembler.assemble(booking));
        } catch (IllegalArgumentException e) {
            // 送るべき中身が無い。**画面で理由を示す**（行き止まりにしない）
            model.addAttribute("rejected", e.getMessage());
        }
        return "booking/notification";
    }

    /** 通知を送って記録する。**送れなかった理由はそのまま予約詳細に返す。** */
    @PostMapping
    public String send(
            @PathVariable("bookingId") String bookingId,
            Principal principal,
            RedirectAttributes redirect) {

        BookingId id;
        try {
            id = BookingId.of(bookingId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }

        var result = notifyService.notifyRoute(
                id, principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED -> redirect.addFlashAttribute("flashError", result.reason());
            default -> redirect.addFlashAttribute("flashSuccess", "荷主に経路を通知しました");
        }
        return REDIRECT_DETAIL + bookingId;
    }

    /**
     * 引取確認コードを再度伝える（US35 / C7）。
     *
     * <p><strong>再発行はしない。</strong> 発行し直すと、元のコードを持って港に来た
     * 荷受人が弾かれる。伝えるのは<strong>いま有効なコードそのもの</strong>である。
     *
     * <p>アクセスできるのは ROLE_SALES のみ（{@code SecurityConfig} の {@code /bookings/**}）。
     * <strong>コードは「受け取ってよい人か」を確かめる秘密の値であり、
     * 誰に伝えたかの記録が残る形にする。</strong>
     */
    @PostMapping("/claim-code")
    public String resendClaimCode(
            @PathVariable("bookingId") String bookingId,
            Principal principal,
            RedirectAttributes redirect) {

        BookingId id;
        try {
            id = BookingId.of(bookingId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }

        var result = resendClaimCodeService.resend(
                id, principal == null ? "unknown" : principal.getName());
        switch (result.outcome()) {
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED -> redirect.addFlashAttribute("flashError", result.reason());
            default -> redirect.addFlashAttribute(
                    "flashSuccess", "引取確認コードを荷主に伝えました");
        }
        return REDIRECT_DETAIL + bookingId;
    }
}
