package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommandService;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.ShipperExistenceChecker;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.shared.application.paging.PageLinks;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 貨物予約の画面（US04）。
 *
 * <p>読み取りは {@link BookingQueryService} を経由する（CQRS のクエリ側）。
 *
 * <p><strong>状態を進める操作は {@link BookingProgressController} に置く。</strong>
 * 遷移は実行するロールが操作ごとに異なり（営業担当者・追跡管理者）、
 * 認可の規則もそこに集まる。一覧・登録・詳細と混ぜると、どの操作が誰のものか
 * 読み取れなくなる。
 */
@Controller
@RequestMapping("/bookings")
public class BookingController {

    private static final String VIEW_LIST = "booking/list";
    private static final String VIEW_FORM = "booking/form";
    private static final String VIEW_DETAIL = "booking/detail";
    private static final String REDIRECT_DETAIL = "redirect:/bookings/";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String UNKNOWN_ACTOR = "unknown";
    private static final String NOT_FOUND_MESSAGE = "予約が見つかりません";

    private final BookCargoCommandService bookService;
    private final BookingQueryService queryService;
    private final ShipperExistenceChecker shipperExistenceChecker;
    private final Clock clock;

    public BookingController(
            BookCargoCommandService bookService,
            BookingQueryService queryService,
            ShipperExistenceChecker shipperExistenceChecker,
            Clock clock) {
        this.bookService = bookService;
        this.queryService = queryService;
        this.shipperExistenceChecker = shipperExistenceChecker;
        this.clock = clock;
    }

    @GetMapping
    public String list(
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            Model model) {
        model.addAttribute("bookings",
                queryService.search(origin, destination, status, PageRequest.of(page)));
        model.addAttribute("query", new PageLinks()
                .with("origin", origin).with("destination", destination)
                .with("status", status).queryPrefix());
        model.addAttribute("origin", origin == null ? "" : origin);
        model.addAttribute("destination", destination == null ? "" : destination);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statuses", BookingStatus.values());
        return VIEW_LIST;
    }

    /**
     * 登録フォーム。
     *
     * <p>荷主詳細の {@code [この荷主で予約する]} から遷移した場合は荷主コードを埋める。
     * **荷主コードを覚えて画面を往復するのが現場で最もストレスになる**（IT1 のレビュー）。
     */
    @GetMapping("/new")
    public String newForm(
            @RequestParam(name = "shipperCode", required = false) String shipperCode,
            Model model) {
        BookingForm form = new BookingForm();
        form.setShipperCode(shipperCode);
        model.addAttribute("form", form);
        model.addAttribute("cargoTypes", CargoType.values());
        return VIEW_FORM;
    }

    @PostMapping
    public String book(
            @Valid @ModelAttribute("form") BookingForm form,
            BindingResult binding,
            Model model,
            Principal principal,
            RedirectAttributes redirect) {

        model.addAttribute("cargoTypes", CargoType.values());
        if (binding.hasErrors()) {
            return VIEW_FORM;
        }

        Optional<ShipperId> shipperId =
                shipperExistenceChecker.findIdByShipperCode(form.getShipperCode());
        if (shipperId.isEmpty()) {
            binding.rejectValue("shipperCode", "notFound", "該当する荷主がありません");
            return VIEW_FORM;
        }

        BookCargoCommand command;
        try {
            command = toCommand(form, shipperId.get());
        } catch (IllegalArgumentException e) {
            // 出発地 = 目的地、到着期限が過去、寸法の入力漏れ。
            // **ドメインが拒否した理由をそのまま画面に返す。** 500 にしない
            binding.reject("domain", e.getMessage());
            return VIEW_FORM;
        }

        var result = bookService.book(command, principal == null ? UNKNOWN_ACTOR : principal.getName());
        switch (result.outcome()) {
            case SHIPPER_NOT_FOUND -> {
                binding.rejectValue("shipperCode", "notFound", "該当する荷主がありません");
                return VIEW_FORM;
            }
            case UNKNOWN_PORTS -> {
                // **どの港が登録されていないかを示す。** 「登録できません」だけでは直せない
                binding.reject("unknownPorts", "港マスタに登録されていない港があります: "
                        + result.unknownPorts().stream()
                                .map(Location::unlocode)
                                .collect(Collectors.joining(", ")));
                return VIEW_FORM;
            }
            default -> { /* 登録できたので下へ進む */ }
        }

        redirect.addFlashAttribute(FLASH_SUCCESS,
                "予約 " + result.cargo().bookingId().value() + " を登録しました（仮予約）");
        return REDIRECT_DETAIL + result.cargo().bookingId().value();
    }

    @GetMapping("/{bookingId}")
    public String detail(@PathVariable String bookingId, Model model) {
        model.addAttribute("booking", queryService.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE)));
        return VIEW_DETAIL;
    }

    private BookCargoCommand toCommand(BookingForm form, ShipperId shipperId) {
        LocalDate today = LocalDate.now(clock);
        CargoSpecification spec = new CargoSpecification(
                CargoType.valueOf(form.getCargoType()),
                Weight.ofKilograms(form.getWeight()),
                Dimensions.ofNullableCentimeters(
                        form.getDimensionLength(), form.getDimensionWidth(),
                        form.getDimensionHeight()),
                Quantity.ofNullable(form.getQuantity()),
                Description.ofNullable(form.getDescription()));
        RouteSpecification route = RouteSpecification.of(
                Location.of(form.getOrigin()),
                Location.of(form.getDestination()),
                form.getArrivalDeadline(),
                today);
        return new BookCargoCommand(shipperId, spec, route);
    }
}
