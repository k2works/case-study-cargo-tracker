package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.interfaces.web.dto.RoutingSearchForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ルート検索 Web MVC コントローラー。
 *
 * <p>予約 ID 起点の検索（{@code ?bookingId}）と直接条件指定の再検索
 * ({@code ?originLocode=...}) の 2 つのフローをサポートする。
 */
@Controller
@RequestMapping("/routings")
public class RoutingWebController {

    private final Optional<RouteSearchService> routeSearchService;
    private final BookingQueryPort bookingQueryPort;
    private final RouteDesignConditionQueryService routeDesignConditionQueryService;

    public RoutingWebController(Optional<RouteSearchService> routeSearchService,
                                BookingQueryPort bookingQueryPort,
                                RouteDesignConditionQueryService routeDesignConditionQueryService) {
        this.routeSearchService = routeSearchService;
        this.bookingQueryPort = bookingQueryPort;
        this.routeDesignConditionQueryService = routeDesignConditionQueryService;
    }

    @ModelAttribute("cargoTypes")
    public CargoType[] cargoTypes() {
        return CargoType.values();
    }

    /**
     * 経路設計条件を表示する。
     */
    @GetMapping("/design-condition")
    public String designCondition(@RequestParam UUID bookingId, Model model) {
        var condition = routeDesignConditionQueryService.findByBookingId(bookingId);
        model.addAttribute("condition", condition);
        model.addAttribute("bookingId", bookingId);
        return "routing/design-condition";
    }

    /**
     * ルート候補を検索して一覧表示する。
     *
     * <ul>
     *   <li>{@code originLocode} 指定時: 入力条件をそのままルート検索する（再検索）。
     *       {@code bookingId} も同時に指定された場合は「予約詳細に戻る」リンク用に model へ保持する。</li>
     *   <li>{@code bookingId} のみ指定時: 予約情報を取得してルート検索する</li>
     *   <li>いずれも指定なし: 予約一覧へリダイレクトする</li>
     * </ul>
     */
    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) String originLocode,
            @RequestParam(required = false) String destinationLocode,
            @RequestParam(required = false) LocalDate requestedArrivalDate,
            @RequestParam(required = false) CargoType cargoType,
            @RequestParam(required = false) BigDecimal weightKg,
            Model model) {

        if (routeSearchService.isEmpty()) {
            return "redirect:/bookings";
        }

        RoutingSearchForm form;

        if (originLocode != null) {
            form = new RoutingSearchForm(
                    originLocode, destinationLocode, requestedArrivalDate, cargoType, weightKg);
            if (bookingId != null) {
                model.addAttribute("bookingId", bookingId);
            }
        } else if (bookingId != null) {
            var snapshot = bookingQueryPort.findById(bookingId)
                    .orElseThrow(() -> new BookingDataNotFoundException(bookingId));
            form = RoutingSearchForm.from(snapshot);
            model.addAttribute("bookingId", bookingId);
        } else {
            return "redirect:/bookings";
        }

        List<RouteCandidate> candidates = routeSearchService.get().searchByCondition(form.toQuery());
        model.addAttribute("candidates", candidates);
        model.addAttribute("form", form);
        return "routing/search";
    }

    @ExceptionHandler(BookingDataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleBookingNotFound(BookingDataNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "error/404";
    }
}
