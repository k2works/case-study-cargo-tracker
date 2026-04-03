package com.example.cargotracker.billing.interfaces.web.dto;

import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 輸送料金算出フォーム DTO。
 */
public class FreightChargeForm {

    @NotBlank
    private String bookingId;

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public CalculateFreightCommand toCommand() {
        return new CalculateFreightCommand(bookingId);
    }
}
