package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.shared.application.paging.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 荷主への通知待ちの予約（US12 の作業入口）。
 *
 * <p><strong>営業には経路が確定したことが伝わらない。</strong> 経路を割り当てるのは
 * 経路設計者であり、ADR-006 により通知も送られない。通知すべき予約を探すために
 * 予約を 1 件ずつ開いて通知履歴を見るのは、<strong>「送ったつもり」を検知するという
 * US12 の目的を運用で壊す</strong>。
 *
 * <p>経路割り当て待ち・追跡番号発行待ちと同じ形にして、営業にも朝の起点を与える。
 */
@Controller
public class NotificationQueueController {

    private final BookingQueryService queryService;

    public NotificationQueueController(BookingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/bookings/notification-queue")
    public String queue(
            @RequestParam(name = "page", required = false) Integer page, Model model) {
        model.addAttribute("bookings", queryService.findAwaitingNotification(PageRequest.of(page)));
        model.addAttribute("query", "");
        return "booking/notification-queue";
    }
}
