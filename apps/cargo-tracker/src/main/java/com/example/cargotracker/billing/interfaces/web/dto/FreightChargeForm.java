package com.example.cargotracker.billing.interfaces.web.dto;

import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 輸送料金算出フォーム DTO。
 */
public class FreightChargeForm {

    @NotBlank
    private String bookingId;
    private BigDecimal adjustmentAmount;

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public BigDecimal getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public void setAdjustmentAmount(BigDecimal adjustmentAmount) {
        this.adjustmentAmount = adjustmentAmount;
    }

    public CalculateFreightCommand toCommand() {
        return new CalculateFreightCommand(bookingId, adjustmentAmount);
    }
}
