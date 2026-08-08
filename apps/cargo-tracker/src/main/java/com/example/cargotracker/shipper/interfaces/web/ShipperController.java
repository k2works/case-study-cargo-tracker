package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shared.application.paging.PageLinks;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.commandservices.ShipperCorrection;
import com.example.cargotracker.shipper.application.internal.commandservices.UpdateShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperView;
import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.ContractNumber;
import com.example.cargotracker.shipper.domain.model.CorporateContract;
import com.example.cargotracker.shipper.domain.model.DiscountRate;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import jakarta.validation.Valid;
import java.security.Principal;
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
 * 荷主の画面（US02 / US32）。
 *
 * <p>アクセスできるのは ROLE_SALES のみ（{@code SecurityConfig}）。
 * 登録・訂正の成功時は PRG で詳細へリダイレクトする。
 *
 * <p>読み取りは {@link ShipperQueryService} を経由する。**Controller が
 * リポジトリを直接呼ぶと集約を 1 件ずつ読む実装が自然に生まれる**ため、
 * ArchUnit ルールで禁じている。
 */
@Controller
@RequestMapping("/shippers")
public class ShipperController {

    private static final String VIEW_LIST = "shipper/list";
    private static final String VIEW_FORM = "shipper/form";
    private static final String VIEW_DETAIL = "shipper/detail";
    private static final String VIEW_EDIT = "shipper/edit";
    private static final String ATTR_SHIPPER = "shipper";
    private static final String PARAM_KEYWORD = "keyword";
    private static final String REDIRECT_DETAIL = "redirect:/shippers/";

    private final RegisterShipperCommandService registerService;
    private final UpdateShipperCommandService updateService;
    private final ShipperQueryService queryService;

    public ShipperController(
            RegisterShipperCommandService registerService,
            UpdateShipperCommandService updateService,
            ShipperQueryService queryService) {
        this.registerService = registerService;
        this.updateService = updateService;
        this.queryService = queryService;
    }

    /** 一覧。キーワードで荷主名・荷主コード・メールアドレスを絞り込む（IT1 持ち越し C3）。 */
    @GetMapping
    public String list(
            @RequestParam(name = PARAM_KEYWORD, required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            Model model) {
        model.addAttribute("shippers", queryService.search(keyword, PageRequest.of(page)));
        model.addAttribute(PARAM_KEYWORD, keyword == null ? "" : keyword);
        model.addAttribute("query", new PageLinks().with(PARAM_KEYWORD, keyword).queryPrefix());
        return VIEW_LIST;
    }

    /**
     * 荷主選択のモーダルに差し込む一覧（htmx の部分更新）。
     *
     * <p>貨物予約登録から荷主コードを調べるために開く。**別タブで一覧を開かせると
     * 画面を往復することになり、荷主コードを書き写す手間が残る**（`ui_design.md`）。
     *
     * <p>リテラルのパスは {@code /{shipperId}} より優先されるため、
     * 荷主 ID として解釈されることはない。
     */
    @GetMapping("/picker")
    public String picker(
            @RequestParam(name = PARAM_KEYWORD, required = false) String keyword, Model model) {
        model.addAttribute("shippers", queryService.search(keyword, PageRequest.of(1)));
        model.addAttribute(PARAM_KEYWORD, keyword == null ? "" : keyword);
        return "shipper/_picker :: rows";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ShipperForm());
        return VIEW_FORM;
    }

    @PostMapping
    public String register(
            @Valid @ModelAttribute("form") ShipperForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return VIEW_FORM;
        }

        CorporateContract contract;
        try {
            contract = contractOf(form);
        } catch (IllegalArgumentException e) {
            // 契約番号の欠落・割引率の値域。**上限の判断はドメインが持つ**ため、
            // そのことばをそのまま返す
            binding.reject("shipper.contract", e.getMessage());
            return VIEW_FORM;
        }

        var result = registerService.register(
                new ShipperName(form.getName()),
                new Email(form.getEmail()),
                new Phone(form.getPhone()),
                new Address(
                        form.getAddressCountry(), form.getAddressPostalCode(),
                        form.getAddressRegion(), form.getAddressCity(), form.getAddressStreet()),
                contract);

        if (result.duplicated()) {
            // 登録せず既存を提示する。どちらを使うかは利用者が決める（US02）
            model.addAttribute("existingShipper", result.shipper());
            return VIEW_FORM;
        }

        redirect.addFlashAttribute(
                "flashSuccess", "荷主 " + result.shipper().shipperCode().value() + " を登録しました");
        return REDIRECT_DETAIL + result.shipper().id().value();
    }

    /**
     * 画面の入力から法人契約を組み立てる（US03）。
     *
     * <p>個人を選んだときは {@code null} を返す。<strong>種別を選び直す前に
     * 打っていた契約の入力は捨てる</strong>（捨てずに弾くと、種別を変えるたびに
     * 入力し直させることになる）。
     *
     * <p>割引率は画面では百分率（{@code 10.00}）で受け取り、
     * <strong>ここで小数（{@code 0.1000}）へ直す</strong>。ドメインと DB の
     * 値域はどちらも小数であり、画面の都合をドメインへ持ち込まない。
     */
    private static CorporateContract contractOf(ShipperForm form) {
        if (!form.isCorporate()) {
            return null;
        }
        java.math.BigDecimal percentage = form.getDiscountRate() == null
                ? java.math.BigDecimal.ZERO : form.getDiscountRate();
        return new CorporateContract(
                new ContractNumber(form.getContractNumber()),
                new DiscountRate(percentage.divide(
                        new java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)));
    }

    /**
     * 訂正フォームから法人契約を組み立てる（US03 + US32）。
     *
     * <p>契約番号が空なら契約を変えない（{@code null} を返す）。
     * <strong>個人荷主かどうかはここで判定しない。</strong> 種別は画面から受け取らない値であり、
     * 判定材料を持たない。適用の可否はアプリケーション層が現在の荷主を見て決める。
     */
    private static CorporateContract contractOf(ShipperEditForm form) {
        if (form.getContractNumber() == null || form.getContractNumber().isBlank()) {
            return null;
        }
        java.math.BigDecimal percentage = form.getDiscountRate() == null
                ? java.math.BigDecimal.ZERO : form.getDiscountRate();
        return new CorporateContract(
                new ContractNumber(form.getContractNumber()),
                new DiscountRate(percentage.divide(
                        new java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)));
    }

    @GetMapping("/{shipperId}")
    public String detail(@PathVariable String shipperId, Model model) {
        model.addAttribute(ATTR_SHIPPER, findOrThrow(shipperId));
        return VIEW_DETAIL;
    }

    /** 訂正フォーム（US32）。 */
    @GetMapping("/{shipperId}/edit")
    public String editForm(@PathVariable String shipperId, Model model) {
        ShipperView shipper = findOrThrow(shipperId);
        model.addAttribute(ATTR_SHIPPER, shipper);
        model.addAttribute("form", toForm(shipper));
        return VIEW_EDIT;
    }

    /** 訂正（US32）。 */
    @PostMapping("/{shipperId}/edit")
    public String update(
            @PathVariable String shipperId,
            @Valid @ModelAttribute("form") ShipperEditForm form,
            BindingResult binding,
            Model model,
            Principal principal,
            RedirectAttributes redirect) {

        ShipperView shipper = findOrThrow(shipperId);
        if (binding.hasErrors()) {
            model.addAttribute(ATTR_SHIPPER, shipper);
            return VIEW_EDIT;
        }

        CorporateContract contract;
        try {
            contract = contractOf(form);
        } catch (IllegalArgumentException e) {
            // 割引率の値域・契約番号の形式。**登録側と同じ扱いにする。**
            // 片方だけ 500 になると、利用者から見て振る舞いが揃わない
            binding.reject("shipper.contract", e.getMessage());
            model.addAttribute(ATTR_SHIPPER, shipper);
            return VIEW_EDIT;
        }

        var result = updateService.update(
                ShipperId.of(shipperId),
                form.getVersion(),
                new ShipperCorrection(
                        new ShipperName(form.getName()),
                        new Email(form.getEmail()),
                        new Phone(form.getPhone()),
                        new Address(
                                form.getAddressCountry(), form.getAddressPostalCode(),
                                form.getAddressRegion(), form.getAddressCity(),
                                form.getAddressStreet()),
                        contract),
                principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case UPDATED -> {
                redirect.addFlashAttribute("flashSuccess", "荷主情報を訂正しました");
                return REDIRECT_DETAIL + shipperId;
            }
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "荷主が見つかりません");
            case DUPLICATED_EMAIL -> {
                // 訂正せず既存の荷主を提示する（US32 の受入基準）
                model.addAttribute("existingShipper", result.shipper());
                model.addAttribute(ATTR_SHIPPER, shipper);
                return VIEW_EDIT;
            }
            default -> {
                // 楽観的ロックの競合。**後勝ちで黙って上書きしない**
                model.addAttribute("conflicted", true);
                model.addAttribute(ATTR_SHIPPER, findOrThrow(shipperId));
                form.setVersion(result.shipper().version());
                return VIEW_EDIT;
            }
        }
    }

    private ShipperView findOrThrow(String shipperId) {
        return queryService.findById(shipperId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "荷主が見つかりません"));
    }

    private static ShipperEditForm toForm(ShipperView shipper) {
        ShipperEditForm form = new ShipperEditForm();
        form.setVersion(shipper.version());
        form.setName(shipper.name());
        form.setEmail(shipper.email());
        form.setPhone(shipper.phone());
        form.setAddressCountry(shipper.addressCountry());
        form.setAddressPostalCode(shipper.addressPostalCode());
        form.setAddressRegion(shipper.addressRegion());
        form.setAddressCity(shipper.addressCity());
        if (shipper.hasContract()) {
            form.setContractNumber(shipper.contractNumber());
            form.setDiscountRate(shipper.discountRatePercentage());
        }
        form.setAddressStreet(shipper.addressStreet());
        return form;
    }
}
