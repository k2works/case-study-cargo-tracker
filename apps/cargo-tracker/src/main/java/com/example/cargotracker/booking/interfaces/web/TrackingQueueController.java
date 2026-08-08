package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.shared.application.paging.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 追跡番号発行待ち一覧（US14）。<strong>追跡管理者の作業入口</strong>である。
 *
 * <p>US13 で営業担当者が確定した予約は、この画面に現れる。<strong>ADR-006 により
 * 通知は送らない。</strong> 確定した予約がここに現れることが、業務上の
 * 「発行依頼」である（US13 の受入基準）。
 *
 * <p><strong>Booking Context に置くのは、扱うのが予約（{@code Cargo}）だからである。</strong>
 * URL が {@code /tracking/} で始まるのは、利用者から見た業務の区切りが追跡だからであり、
 * <strong>URL の語と内部のコンテキスト構成は一致しなくてよい</strong>
 * （経路割り当て待ちが {@code /routing/queue} で Booking にあるのと同じ）。
 *
 * <p>Tracking Context に置くと、追跡が Booking のクエリサービスを直接参照することになり
 * ArchUnit ルール 4 に落ちる。<strong>作業入口は「何を扱うか」で置き場を決める。</strong>
 */
@Controller
@RequestMapping("/tracking")
public class TrackingQueueController {

    private final BookingQueryService queryService;

    public TrackingQueueController(BookingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/queue")
    public String queue(
            @RequestParam(name = "page", required = false) Integer page, Model model) {
        model.addAttribute("bookings", queryService.findAwaitingTracking(PageRequest.of(page)));
        // **発行後の貨物への入口をここに置く**（US17）。発行待ち一覧は発行した時点で
        // その予約が消えるため、状態を手で更新したい追跡管理者の行き先が無かった
        model.addAttribute("inTransit", queryService.findInTransit(PageRequest.of(null)));
        model.addAttribute("query", "");
        return "tracking/queue";
    }
}
