package com.example.cargotracker.estimation.interfaces.web;

import com.example.cargotracker.estimation.application.internal.queryservices.EstimateQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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

    public EstimateController(EstimateQueryService queryService) {
        this.queryService = queryService;
    }

    /** 見積一覧。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("estimates", queryService.findAll());
        return "estimates/list";
    }
}
