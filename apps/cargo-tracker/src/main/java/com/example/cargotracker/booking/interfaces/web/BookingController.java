package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommandService;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.ShipperExistenceChecker;
import com.example.cargotracker.booking.application.internal.queryservices.BookingNotificationQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingSearchCriteria;
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

    /**
     * 紐付けの無い荷主に使う荷主 ID。
     *
     * <p><strong>どの予約にも一致しない値である。</strong> 「絞らない」ではなく
     * 「0 件に絞る」ことを、SQL の条件として表す。
     */
    private static final java.util.UUID NO_SHIPPER =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final BookCargoCommandService bookService;
    private final BookingQueryService queryService;
    private final ShipperExistenceChecker shipperExistenceChecker;
    private final BookingNotificationQueryService notificationQueryService;
    private final com.example.cargotracker.shared.application.security.CurrentUser currentUser;
    private final Clock clock;

    public BookingController(
            BookCargoCommandService bookService,
            BookingQueryService queryService,
            ShipperExistenceChecker shipperExistenceChecker,
            BookingNotificationQueryService notificationQueryService,
            com.example.cargotracker.shared.application.security.CurrentUser currentUser,
            Clock clock) {
        this.currentUser = currentUser;
        this.bookService = bookService;
        this.queryService = queryService;
        this.shipperExistenceChecker = shipperExistenceChecker;
        this.notificationQueryService = notificationQueryService;
        this.clock = clock;
    }

    @GetMapping
    public String list(
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "trackingNumber", required = false) String trackingNumber,
            @RequestParam(name = "page", required = false) Integer page,
            Model model) {
        model.addAttribute("bookings",
                queryService.search(scoped(origin, destination, status, trackingNumber),
                        PageRequest.of(page)));
        // **絞り込みの条件をページ送りのリンクに残す。** 残さないと 2 ページ目で
        // 条件が消え、探していた予約が一覧から消える
        model.addAttribute("query", new PageLinks()
                .with("origin", origin).with("destination", destination)
                .with("status", status).with("trackingNumber", trackingNumber)
                .queryPrefix());
        model.addAttribute("origin", origin == null ? "" : origin);
        model.addAttribute("destination", destination == null ? "" : destination);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("trackingNumber", trackingNumber == null ? "" : trackingNumber);
        model.addAttribute("statuses", BookingStatus.values());
        return VIEW_LIST;
    }

    /**
     * 登録フォーム。
     *
     * <p>荷主詳細の {@code [この荷主で予約する]} から遷移した場合は荷主コードを埋める。
     * **荷主コードを覚えて画面を往復するのが現場で最もストレスになる**（IT1 のレビュー）。
     */
    /**
     * 貨物種別に応じた入力欄（US05。htmx で差し替える）。
     *
     * <p><strong>種別ごとの欄を常に出しておかない。</strong> 危険物にも冷凍にも
     * ならない予約が大半であり、常時出すと入力欄が 6 つ増えて主要な項目が埋もれる。
     *
     * <p><strong>押せない欄を見せない</strong>という方針は、荷主種別の出し分けを
     * 「常に出す」にした US03 とは逆である。あちらは<strong>種別を選ぶ前</strong>に
     * 入力できることが要り、こちらは<strong>種別が決まってから</strong>入力する。
     */
    @GetMapping("/new/specification")
    public String specificationFields(
            @ModelAttribute("form") BookingForm form,
            @RequestParam(name = "cargoType", defaultValue = "GENERAL") String cargoType,
            Model model) {
        CargoType type;
        try {
            type = CargoType.valueOf(cargoType);
        } catch (IllegalArgumentException e) {
            type = CargoType.GENERAL;
        }
        model.addAttribute("cargoType", type);
        // **入力済みの値を持ち帰る。** 種別を選び直しただけで申告が消えると、
        // UN 番号のような書類から転記する値を二度入力することになる
        // （フォーム全体を hx-include で送っているのは、そのためである）
        return "booking/_specification :: fields";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(name = "shipperCode", required = false) String shipperCode,
            Model model) {
        BookingForm form = new BookingForm();
        form.setShipperCode(shipperCode);
        model.addAttribute("form", form);
        model.addAttribute("cargoTypes", CargoType.values());
        model.addAttribute("cargoType", selectedType(form));
        return VIEW_FORM;
    }

    /** 選択中の貨物種別。未選択・不正な値は一般貨物として扱う。 */
    private static CargoType selectedType(BookingForm form) {
        if (form.getCargoType() == null || form.getCargoType().isBlank()) {
            return CargoType.GENERAL;
        }
        try {
            return CargoType.valueOf(form.getCargoType());
        } catch (IllegalArgumentException e) {
            return CargoType.GENERAL;
        }
    }

    @PostMapping
    public String book(
            @Valid @ModelAttribute("form") BookingForm form,
            BindingResult binding,
            Model model,
            Principal principal,
            RedirectAttributes redirect) {

        model.addAttribute("cargoTypes", CargoType.values());
        // **差し戻したときに特別な入力欄を消さない。** 欄が消えると、
        // 「危険物申告が必要です」と言われた利用者が入れる場所を失う
        model.addAttribute("cargoType", selectedType(form));
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
        var booking = queryService.findById(bookingId)
                // **他社の予約は「無い」と答える**（US34）。403 は「存在するが見せない」と
                // 伝えてしまい、番号を変えながら叩けば他社の予約の有無を確かめられる。
                // 追跡照会（US18）で「存在しない番号と権限外の番号を区別しない」と
                // 決めたのと同じ判断である
                .filter(this::visibleToCurrentUser)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        model.addAttribute("booking", booking);
        // 通知履歴は**常時表示する**（US12）。残しても見えなければ確認できない
        model.addAttribute("notifications",
                notificationQueryService.findByBookingId(bookingId));
        return VIEW_DETAIL;
    }

    /**
     * 荷主として絞り込むべき利用者なら、絞り込みを条件に足す（US34）。
     *
     * <p><strong>絞り込みは利用者が外せない条件である。</strong> 画面から渡された条件に
     * 上書きさせず、ここで必ず足す。
     *
     * <p><strong>紐付けが無い荷主は 0 件に絞る。</strong> 「紐付けが無い = 絞らない」に
     * すると、設定を忘れた荷主に全社の予約が見える。
     * <strong>設定漏れが情報漏洩に直結する形を作らない。</strong>
     */
    private BookingSearchCriteria scoped(
            String origin, String destination, String status, String trackingNumber) {
        BookingSearchCriteria criteria =
                BookingSearchCriteria.of(origin, destination, status, trackingNumber);
        if (!currentUser.scopedToShipper()) {
            return criteria;
        }
        return criteria.scopedTo(currentUser.linkedShipperId()
                .map(ShipperId::value)
                // 紐付けが無い荷主。**存在しない荷主 ID で絞り、0 件にする**
                .orElse(NO_SHIPPER));
    }

    /**
     * いまの利用者が見てよい予約か（US34）。
     *
     * <p>社内利用者はすべて見る。荷主は<strong>自分に紐づく予約だけ</strong>を見る。
     * <strong>紐付けが無い荷主は 1 件も見ない。</strong>
     */
    private boolean visibleToCurrentUser(
            com.example.cargotracker.booking.application.internal.queryservices.BookingView
                    booking) {
        if (!currentUser.scopedToShipper()) {
            return true;
        }
        return currentUser.linkedShipperId()
                .map(id -> id.value().toString().equals(booking.shipperId()))
                .orElse(Boolean.FALSE);
    }

    private BookCargoCommand toCommand(BookingForm form, ShipperId shipperId) {
        LocalDate today = LocalDate.now(clock);
        CargoSpecification spec = CargoSpecification.create(
                CargoType.valueOf(form.getCargoType()),
                Weight.ofKilograms(form.getWeight()),
                Dimensions.ofNullableCentimeters(
                        form.getDimensionLength(), form.getDimensionWidth(),
                        form.getDimensionHeight()),
                Quantity.ofNullable(form.getQuantity()),
                Description.ofNullable(form.getDescription()),
                // **種別との整合は CargoSpecification が守る**（US05）。
                // ここでは入力を値オブジェクトに直すだけで、必須かどうかは判断しない
                com.example.cargotracker.booking.domain.model.HazardousDeclaration.ofNullable(
                        form.getHazardClass(), form.getUnNumber(),
                        form.getProperShippingName()).orElse(null),
                com.example.cargotracker.booking.domain.model.TemperatureRequirement.ofNullable(
                        form.getMinTemperature(), form.getMaxTemperature(),
                        form.getTemperatureUnit()).orElse(null));
        RouteSpecification route = RouteSpecification.of(
                Location.of(form.getOrigin()),
                Location.of(form.getDestination()),
                form.getArrivalDeadline(),
                today);
        return new BookCargoCommand(shipperId, spec, route);
    }
}
