package com.example.routingms.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 航海スケジュールの登録・更新リクエスト（US24・US25）。 */
public record VoyageRequest(
        @NotBlank(message = "航海番号は必須です") String voyageNumber,
        @NotBlank(message = "船名は必須です") String vesselName,
        @NotBlank(message = "運送会社は必須です") String carrierName,
        @NotEmpty(message = "対応できる貨物種別を 1 つ以上選んでください")
        List<String> supportedCargoTypes,
        @NotEmpty(message = "寄港地を 1 区間以上入力してください")
        List<@Valid MovementRequest> movements) {

    /** 1 区間分。 */
    public record MovementRequest(
            @NotBlank(message = "区間の出発地は必須です") String departureUnLocode,
            @NotBlank(message = "区間の到着地は必須です") String arrivalUnLocode,
            @NotNull(message = "区間の出発日時は必須です") Instant departureTime,
            @NotNull(message = "区間の到着日時は必須です") Instant arrivalTime) {
    }
}
