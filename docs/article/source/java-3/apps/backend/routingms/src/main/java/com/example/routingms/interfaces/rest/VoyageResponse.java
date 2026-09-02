package com.example.routingms.interfaces.rest;

import com.example.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.routingms.domain.model.aggregates.Voyage;
import java.time.Instant;
import java.util.List;

/** 航海スケジュールの応答。 */
public record VoyageResponse(
        String voyageNumber,
        String vesselName,
        String carrierName,
        List<String> supportedCargoTypes,
        String originUnLocode,
        String originName,
        String destinationUnLocode,
        String destinationName,
        Instant departureTime,
        Instant arrivalTime,
        List<MovementResponse> movements) {

        public VoyageResponse {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        supportedCargoTypes = supportedCargoTypes == null ? null : List.copyOf(supportedCargoTypes);
        movements = movements == null ? null : List.copyOf(movements);
        }


    /** 1 区間分。地点は名称も返す（画面に対訳表を持たせない）。 */
    public record MovementResponse(
            String departureUnLocode,
            String departureName,
            String arrivalUnLocode,
            String arrivalName,
            Instant departureTime,
            Instant arrivalTime) {

        static MovementResponse from(CarrierMovement movement) {
            return new MovementResponse(
                    movement.departureLocation().unLocode(), movement.departureLocation().name(),
                    movement.arrivalLocation().unLocode(), movement.arrivalLocation().name(),
                    movement.departureTime(), movement.arrivalTime());
        }
    }

    public static VoyageResponse from(Voyage voyage) {
        return new VoyageResponse(
                voyage.voyageNumber().value(), voyage.vesselName(), voyage.carrierName(),
                voyage.supportedCargoTypes().stream().map(Enum::name).sorted().toList(),
                voyage.schedule().origin().unLocode(), voyage.schedule().origin().name(),
                voyage.schedule().destination().unLocode(), voyage.schedule().destination().name(),
                voyage.schedule().carrierMovements().get(0).departureTime(),
                voyage.schedule().carrierMovements()
                        .get(voyage.schedule().carrierMovements().size() - 1).arrivalTime(),
                voyage.schedule().carrierMovements().stream().map(MovementResponse::from).toList());
    }
}
