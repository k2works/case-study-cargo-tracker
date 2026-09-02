package com.example.cargotracker.booking.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 荷主登録の入出力（UI 設計 S11）。 */
public final class ShipperDtos {

    private ShipperDtos() {
    }

    public record RegisterShipperRequest(
            @NotBlank String name,
            @NotNull String shipperType,
            @NotBlank String email,
            String phone,
            String address,
            String contractNumber,
            BigDecimal discountRate) {
    }

    public record RegisterShipperResponse(String shipperId) {
    }

    /** 受け付けたが投影がまだのときに返す（202）。画面は「反映中」を出す。 */
    public record PendingResponse(String shipperId, String message) {
    }
}
