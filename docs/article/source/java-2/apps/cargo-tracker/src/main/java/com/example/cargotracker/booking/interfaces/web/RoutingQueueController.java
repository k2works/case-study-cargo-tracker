package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.shared.application.paging.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 経路割り当て待ち一覧（US06 / US08）。<strong>経路設計者の作業入口</strong>である。
 *
 * <p>US06 で営業担当者から引き渡された予約は、この画面に現れる。
 * **ADR-006 により通知は送らない。** 引き渡した予約がここに現れることが、
 * 業務上の「引き渡し」である（US06 の受入基準）。
 *
 * <p>Booking Context に置くのは、扱うのが予約（{@code Cargo}）だからである。
 * URL が {@code /routing/} で始まるのは、利用者から見た業務の区切りが
 * 経路設計だからであり、**URL の語と内部のコンテキスト構成は一致しなくてよい**
 * （ADR-002 が {@code /handling/*} について述べているのと同じ考え方）。
 */
@Controller
@RequestMapping("/routing")
public class RoutingQueueController {

    private final BookingQueryService queryService;

    public RoutingQueueController(BookingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/queue")
    public String queue(
            @RequestParam(name = "page", required = false) Integer page, Model model) {
        model.addAttribute("bookings", queryService.findAwaitingRouting(PageRequest.of(page)));
        model.addAttribute("query", "");
        return "routing/queue";
    }
}
