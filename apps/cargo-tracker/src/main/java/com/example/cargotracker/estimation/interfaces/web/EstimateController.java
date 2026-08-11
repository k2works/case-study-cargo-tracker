package com.example.cargotracker.estimation.interfaces.web;

import com.example.cargotracker.estimation.application.internal.commandservices
        .CreateEstimateCommandService;
import com.example.cargotracker.estimation.application.internal.queryservices.EstimateQueryService;
import com.example.cargotracker.estimation.domain.model.EstimationCargoType;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    public EstimateController(
            EstimateQueryService queryService, CreateEstimateCommandService createService) {
        this.queryService = queryService;
        this.createService = createService;
    }

    /** 見積一覧。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("estimates", queryService.findAll());
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
     * <p><strong>貨物種別は再表示のたびに引き継ぐ。</strong> 危険物を選んだ状態で
     * 開き直すと申告欄が消えるようでは、入力の途中で気づけない。
     */
    @GetMapping("/new")
    public String form(
            @RequestParam(value = "cargoType", required = false) String cargoType,
            Model model) {
        EstimateForm form = new EstimateForm();
        form.setCargoType(cargoType == null ? EstimationCargoType.GENERAL.name() : cargoType);
        model.addAttribute("estimateForm", form);
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
                    EstimationCargoType.valueOf(form.getCargoType()), form.getWeightKg()));
            if (result.isAccepted()) {
                return "redirect:/estimates/" + result.estimateId();
            }
            binding.reject("estimate.rejected", result.reason());
        }
        model.addAttribute("cargoTypes", EstimationCargoType.values());
        return "estimates/new";
    }
}
