package com.example.cargotracker.routing.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 航海スケジュール登録（S33）の入出力。 */
public final class VoyageDtos {

    private VoyageDtos() {
    }

    /**
     * 登録の入力。
     *
     * <p>寄港地の順序と港の連結、到着が出発より後であることは値オブジェクトと集約が
     * 見る。ここで見るのは「入っているか」だけにして、業務の判断を 2 か所に置かない。</p>
     */
    public record RegisterVoyageRequest(
            @NotBlank String voyageNumber,
            @NotBlank String carrierCode,
            @NotBlank String carrierName,
            @NotBlank String vesselName,
            @NotEmpty @Valid List<MovementRequest> movements,
            List<String> acceptedCargoTypes) {
    }

    /** 寄港地 1 件。並び順がそのまま航海の順序になる。 */
    public record MovementRequest(
            @NotBlank String departureUnLocode,
            @NotBlank String arrivalUnLocode,
            @NotNull Instant departureAt,
            @NotNull Instant arrivalAt) {
    }

    public record RegisterVoyageResponse(String voyageNumber) {
    }

    /** 投影がまだのときの応答。404 と区別する。 */
    public record PendingResponse(String voyageNumber, String message) {
    }
}
