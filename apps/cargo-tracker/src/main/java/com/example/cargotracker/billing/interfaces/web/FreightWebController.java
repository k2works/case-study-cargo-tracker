package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.ApplyDiscountCommandService;
import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort;
import com.example.cargotracker.billing.application.internal.outboundservices.ShipperDiscountQueryPort;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.ApplyDiscountCommand;
import com.example.cargotracker.billing.domain.model.services.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import com.example.cargotracker.billing.interfaces.web.dto.FreightChargeForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 輸送料金 Web コントローラー。
 */
@Controller
@RequestMapping("/freight")
public class FreightWebController {

    private static final String VIEW_LIST = "billing/list";
    private static final String VIEW_CALCULATE = "billing/calculate";
    private static final String REDIRECT_FREIGHT = "redirect:/freight";
    private static final String FORM_ATTRIBUTE = "form";
    private static final String BOOKING_SUMMARY_ATTRIBUTE = "bookingSummary";
    private static final String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";
    private static final String SUCCESS_MESSAGE_ATTRIBUTE = "successMessage";
    private static final String DISCOUNT_RATE_ATTRIBUTE = "discountRate";
    private static final String PREVIEW_BASE_AMOUNT_ATTRIBUTE = "previewBaseAmount";
    private static final String PREVIEW_DISCOUNT_AMOUNT_ATTRIBUTE = "previewDiscountAmount";
    private static final String PREVIEW_TOTAL_AMOUNT_ATTRIBUTE = "previewTotalAmount";

    private final CalculateFreightCommandService calculateFreightCommandService;
    private final ApplyDiscountCommandService applyDiscountCommandService;
    private final FreightChargeQueryService freightChargeQueryService;
    private final FreightBookingQueryPort freightBookingQueryPort;
    private final ShipperDiscountQueryPort shipperDiscountQueryPort;
    private final FreightCalculationService freightCalculationService;
    private final DiscountPolicy discountPolicy;

    public FreightWebController(CalculateFreightCommandService calculateFreightCommandService,
                                ApplyDiscountCommandService applyDiscountCommandService,
                                FreightChargeQueryService freightChargeQueryService,
                                FreightBookingQueryPort freightBookingQueryPort,
                                ShipperDiscountQueryPort shipperDiscountQueryPort,
                                FreightCalculationService freightCalculationService,
                                DiscountPolicy discountPolicy) {
        this.calculateFreightCommandService = calculateFreightCommandService;
        this.applyDiscountCommandService = applyDiscountCommandService;
        this.freightChargeQueryService = freightChargeQueryService;
        this.freightBookingQueryPort = freightBookingQueryPort;
        this.shipperDiscountQueryPort = shipperDiscountQueryPort;
        this.freightCalculationService = freightCalculationService;
        this.discountPolicy = discountPolicy;
    }

    /**
     * 輸送料金一覧を表示する。
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("freightCharges", freightChargeQueryService.findAll());
        return VIEW_LIST;
    }

    /**
     * 料金算出フォームを表示する。
     */
    @GetMapping("/calculate")
    public String showCalculateForm(@RequestParam(value = "bookingId", required = false) String bookingId,
                                    Model model) {
        FreightChargeForm form = new FreightChargeForm();
        if (bookingId != null && !bookingId.isBlank()) {
            form.setBookingId(bookingId);
            freightBookingQueryPort.findCalculableBookingById(bookingId)
                    .ifPresent(summary -> {
                        model.addAttribute(BOOKING_SUMMARY_ATTRIBUTE, summary);
                        BigDecimal previewBaseAmount = freightCalculationService.calculateBaseAmount(
                                summary.weightKg(), summary.cargoType());
                        BigDecimal discountRate = shipperDiscountQueryPort.findDiscountRateByBookingId(bookingId);
                        model.addAttribute(PREVIEW_BASE_AMOUNT_ATTRIBUTE, previewBaseAmount);
                        model.addAttribute(DISCOUNT_RATE_ATTRIBUTE, discountRate);
                        if (discountRate.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal previewDiscountAmount =
                                    discountPolicy.calculateDiscount(previewBaseAmount, discountRate);
                            model.addAttribute(PREVIEW_DISCOUNT_AMOUNT_ATTRIBUTE, previewDiscountAmount);
                            model.addAttribute(
                                    PREVIEW_TOTAL_AMOUNT_ATTRIBUTE,
                                    previewBaseAmount.add(previewDiscountAmount)
                            );
                        }
                    });
        }
        model.addAttribute(FORM_ATTRIBUTE, form);
        return VIEW_CALCULATE;
    }

    /**
     * 輸送料金を算出する。
     */
    @PostMapping("/calculate")
    public String calculate(@Valid @ModelAttribute(FORM_ATTRIBUTE) FreightChargeForm form,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return VIEW_CALCULATE;
        }

        try {
            calculateFreightCommandService.calculate(form.toCommand());
        } catch (BookingNotFoundException e) {
            redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTRIBUTE, e.getMessage());
            return REDIRECT_FREIGHT;
        }

        return REDIRECT_FREIGHT;
    }

    /**
     * 輸送料金を確定する。
     */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable("id") String id) {
        calculateFreightCommandService.confirm(new FreightId(UUID.fromString(id)));
        return REDIRECT_FREIGHT;
    }

    /**
     * 法人割引を適用する。
     */
    @PostMapping("/{id}/apply-discount")
    public String applyDiscount(@PathVariable("id") String id,
                                @RequestParam("bookingId") String bookingId,
                                RedirectAttributes redirectAttributes) {
        try {
            applyDiscountCommandService.applyDiscount(new ApplyDiscountCommand(id, bookingId));
            redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTRIBUTE, "法人割引を適用しました");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTRIBUTE, e.getMessage());
        }
        return REDIRECT_FREIGHT;
    }
}
