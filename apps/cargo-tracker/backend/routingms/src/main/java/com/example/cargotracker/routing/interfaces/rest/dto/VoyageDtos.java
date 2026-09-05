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

    /**
     * 更新の入力（US25）。
     *
     * <p>航海番号は経路（{@code PUT /voyages/{voyageNumber}}）が持つ。本文にも置くと、
     * 2 つが食い違ったときにどちらを正とするかを決めなければならない。</p>
     *
     * <p><b>差し替えであって部分更新ではない。</b> 寄港地は順序を持つ列なので、
     * 一部だけ送る形にすると連結と時刻の検査を集約が通しで行えない。</p>
     */
    public record UpdateVoyageRequest(
            @NotBlank String carrierCode,
            @NotBlank String carrierName,
            @NotBlank String vesselName,
            @NotEmpty @Valid List<MovementRequest> movements,
            List<String> acceptedCargoTypes) {
    }

    /** 更新前後の差分（US25 §受入基準 2）。変わった項目だけが並ぶ。 */
    public record VoyageDiffResponse(
            String voyageNumber,
            List<com.example.cargotracker.routing.interfaces.rest.VoyageScheduleDiff.FieldChange>
                    changes) {
    }

    public record RegisterVoyageResponse(String voyageNumber) {
    }

    /** 投影がまだのときの応答。404 と区別する。 */
    public record PendingResponse(String voyageNumber, String message) {
    }
}
