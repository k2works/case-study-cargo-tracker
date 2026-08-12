package com.example.cargotracker.estimation.interfaces.web;

import com.example.cargotracker.estimation.application.internal.commandservices
        .CreateEstimateCommandService;
import com.example.cargotracker.estimation.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.estimation.application.internal.queryservices.EstimateFilter;
import com.example.cargotracker.estimation.application.internal.queryservices.EstimateQueryService;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimationCargoType;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 見積の画面（US01）。
 *
 * <p><strong>見積は予約の前段である。</strong> 荷主に予算と納期を伝えるために
 * 営業担当者が最初に触る画面であり、ここで入れた条件をそのまま予約へ引き継ぐ。
 */
@Controller
@RequestMapping("/estimates")
public class EstimateController {

    private final EstimateQueryService queryService;
    private final CreateEstimateCommandService createService;
    private final KnownPorts knownPorts;

    public EstimateController(
            EstimateQueryService queryService, CreateEstimateCommandService createService,
            KnownPorts knownPorts) {
        this.queryService = queryService;
        this.createService = createService;
        this.knownPorts = knownPorts;
    }

    /**
     * 見積一覧（{@code ui_design.md}。IT19 の C4）。
     *
     * <p><strong>絞り込みの規則は application 層が持つ</strong>（ADR-022）。
     * ここが決めるのは「画面から受け取った文字列を条件に写すこと」だけである。
     */
    @GetMapping
    public String list(
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(name = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(name = "status", required = false) String status,
            Model model) {
        var criteria = EstimateFilter.Criteria.fromRequest(
                origin, destination, createdFrom, createdTo, status);
        var all = queryService.findAll();
        var estimates = EstimateFilter.apply(all, criteria);
        model.addAttribute("estimates", estimates);
        model.addAttribute("criteria", criteria);
        // **0 件のときは「何をすれば先へ進めるか」を分ける**（U5）。港の実在は
        // 問い合わせだが、どれを先に伝えるかは規則である（ADR-022）。
        // **0 件のときしか引かない** —— 結果が出ている人に余計な往復をさせない
        if (estimates.isEmpty()) {
            model.addAttribute("emptyReason", EstimateFilter.emptyReason(
                    all, criteria, knownPorts.findUnknown(
                            EstimateFilter.requestedPorts(criteria))));
        }
        return "estimates/list";
    }

    /**
     * 見積詳細（US01 の受入基準 3）。
     *
     * <p><strong>見つからない見積は 404 である。</strong> URL を直接編集しただけで
     * 500 になると、入力の誤りが障害に見える。
     */
    @GetMapping("/{estimateId}")
    public String detail(@PathVariable("estimateId") String estimateId, Model model) {
        var view = queryService.findById(estimateId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "見積が見つかりません"));
        model.addAttribute("estimate", view);
        return "estimates/detail";
    }

    /**
     * 見積の作成フォーム。
     *
     * <p>危険物の申告欄はサーバ側で出し分けるため、貨物種別を変えると画面を開き直す。
     * <strong>そのときに入力済みの内容を道連れにしない</strong>（IT18 クローズ前レビュー H1）。
     *
     * <p><strong>荷主と電話しながら打ち込んだ内容が消えると、聞き直すことになる。</strong>
     * 種別を選ぶのが入力の途中でも構わない形にする。
     */
    @GetMapping("/new")
    public String form(@ModelAttribute("estimateForm") EstimateForm form, Model model) {
        if (form.getCargoType() == null || form.getCargoType().isBlank()) {
            form.setCargoType(EstimationCargoType.GENERAL.name());
        }
        model.addAttribute("cargoTypes", EstimationCargoType.values());
        return "estimates/new";
    }

    /**
     * 見積を作る（PRG）。
     *
     * <p><strong>拒んだときはフォームに戻して理由を出す。</strong> 500 にすると、
     * 入力の誤りが障害に見える。
     */
    @PostMapping
    public String create(
            @Valid @ModelAttribute("estimateForm") EstimateForm form,
            BindingResult binding,
            Model model) {
        if (!binding.hasErrors()) {
            var result = createService.create(new CreateEstimateCommandService.Request(
                    form.getOrigin(), form.getDestination(), form.getArrivalDeadline(),
                    EstimationCargoType.valueOf(form.getCargoType()), form.getWeightKg(),
                    // **入力させたものを捨てない**（受入基準 6）
                    com.example.cargotracker.estimation.domain.model.valueobjects.HazardousDeclaration.of(
                            form.getHazardClass(), form.getUnNumber(),
                            form.getProperShippingName())));
            if (result.isAccepted()) {
                return "redirect:/estimates/" + result.estimateId();
            }
            binding.reject("estimate.rejected", result.reason());
        }
        model.addAttribute("cargoTypes", EstimationCargoType.values());
        return "estimates/new";
    }
}
