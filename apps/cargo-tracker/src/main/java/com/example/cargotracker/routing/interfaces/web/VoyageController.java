package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.commandservices.RegisterVoyageCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.CarrierName;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.Schedule;
import com.example.cargotracker.routing.domain.model.VesselName;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.shared.application.paging.PageLinks;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shared.domain.model.Location;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 航路管理の画面（US24 / US07）。
 *
 * <p>アクセスできるのは ROLE_ROUTER のみ（{@code SecurityConfig}）。
 * 読み取りは {@link VoyageQueryService} を経由する（CQRS のクエリ側）。
 */
@Controller
@RequestMapping("/voyages")
public class VoyageController {

    private static final String VIEW_LIST = "voyage/list";
    private static final String VIEW_FORM = "voyage/form";
    private static final String VIEW_DETAIL = "voyage/detail";
    private static final String ATTR_CARGO_TYPES = "cargoTypes";

    private final RegisterVoyageCommandService registerService;
    private final VoyageQueryService queryService;
    private final Clock clock;

    public VoyageController(
            RegisterVoyageCommandService registerService,
            VoyageQueryService queryService,
            Clock clock) {
        this.registerService = registerService;
        this.queryService = queryService;
        this.clock = clock;
    }

    /** 航海スケジュールの検索（US07）。 */
    @GetMapping
    public String list(
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "departureFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureFrom,
            @RequestParam(name = "departureTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureTo,
            @RequestParam(name = "cargoType", required = false) String cargoType,
            @RequestParam(name = "page", required = false) Integer page,
            Model model) {

        RoutingCargoType type = parseCargoType(cargoType);
        model.addAttribute("voyages", queryService.search(
                origin, destination, departureFrom, departureTo, type, PageRequest.of(page)));
        model.addAttribute("origin", origin == null ? "" : origin);
        model.addAttribute("destination", destination == null ? "" : destination);
        model.addAttribute("departureFrom", departureFrom);
        model.addAttribute("departureTo", departureTo);
        model.addAttribute("cargoType", cargoType == null ? "" : cargoType);
        model.addAttribute(ATTR_CARGO_TYPES, RoutingCargoType.values());
        model.addAttribute("query", new PageLinks()
                .with("origin", origin)
                .with("destination", destination)
                .with("departureFrom", departureFrom == null ? null : departureFrom.toString())
                .with("departureTo", departureTo == null ? null : departureTo.toString())
                .with("cargoType", cargoType)
                .queryPrefix());
        return VIEW_LIST;
    }

    /**
     * 航海詳細（IT3 レビュー M1）。
     *
     * <p>一覧の 1 行には端点しか収まらない。<strong>乗り継ぎ便の寄港地ごとの
     * 発着時刻</strong>は、この画面でしか読めない。
     */
    @GetMapping("/{voyageNumber}")
    public String detail(
            @PathVariable("voyageNumber") String voyageNumber,
            Model model) {
        var detail = queryService.findDetail(voyageNumber)
                // URL を直接編集しただけで 500 にしない
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "航海が見つかりません"));
        model.addAttribute("voyage", detail);
        return VIEW_DETAIL;
    }

    /**
     * 運送区間の入力行を 1 本返す（htmx の部分更新。IT3 レビュー M3）。
     *
     * <p><strong>添字はサーバが決める。</strong> ブラウザ側で採番すると、
     * 行の増減で添字が飛んだときにバインドが黙って壊れる。
     */
    @GetMapping("/movements")
    public String movementRow(
            @RequestParam(name = "index", defaultValue = "0") int index,
            Model model) {
        // URL を直接編集して負の添字を入れても壊れないようにする
        model.addAttribute("index", Math.max(index, 0));
        return "voyage/_movement :: row";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new VoyageForm());
        model.addAttribute(ATTR_CARGO_TYPES, RoutingCargoType.values());
        return VIEW_FORM;
    }

    /** 航海スケジュールの登録（US24）。 */
    @PostMapping
    public String register(
            @Valid @ModelAttribute("form") VoyageForm form,
            BindingResult binding,
            Model model,
            Principal principal,
            RedirectAttributes redirect) {

        model.addAttribute(ATTR_CARGO_TYPES, RoutingCargoType.values());
        if (binding.hasErrors()) {
            return VIEW_FORM;
        }

        RegisterVoyageCommand command;
        try {
            command = toCommand(form);
        } catch (IllegalArgumentException e) {
            // 連結制約・時系列・同一港の区間など、項目単体では判定できない業務ルール違反。
            // **ドメインが拒否した理由をそのまま画面に返す。** 500 にしない
            binding.reject("domain", e.getMessage());
            return VIEW_FORM;
        }

        var result = registerService.register(
                command, principal == null ? "unknown" : principal.getName());
        switch (result.outcome()) {
            case DUPLICATED -> {
                binding.rejectValue("voyageNumber", "duplicated",
                        "この航海番号は既に登録されています");
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

        redirect.addFlashAttribute("flashSuccess",
                "航海 " + result.voyage().voyageNumber().value() + " を登録しました");
        return "redirect:/voyages";
    }

    private RoutingCargoType parseCargoType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RoutingCargoType.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 不正な値は「絞り込まない」として扱う。**URL を直接編集しただけで 500 にしない**
            return null;
        }
    }

    private RegisterVoyageCommand toCommand(VoyageForm form) {
        List<CarrierMovement> movements = form.getMovements().stream()
                .map(m -> CarrierMovement.of(
                        Location.of(m.getDeparture()),
                        Location.of(m.getArrival()),
                        m.getDepartureTime().atZone(clock.getZone()).toInstant(),
                        m.getArrivalTime().atZone(clock.getZone()).toInstant()))
                .toList();

        Set<RoutingCargoType> types = form.getCargoTypes().stream()
                .map(RoutingCargoType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoutingCargoType.class)));

        return new RegisterVoyageCommand(
                new VoyageNumber(form.getVoyageNumber()),
                new VesselName(form.getVesselName()),
                new CarrierName(form.getCarrierName()),
                Schedule.of(movements),
                types);
    }
}
