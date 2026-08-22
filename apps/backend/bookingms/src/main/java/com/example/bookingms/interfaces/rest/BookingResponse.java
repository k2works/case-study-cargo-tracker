package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.Cargo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 貨物予約の応答。
 *
 * <p>予約金額は返さない。IT2 では算出できず（US18・IT11）、0 を返すと未算出と無料が
 * 区別できなくなる（ADR-009）。
 */
public record BookingResponse(
        Long id,
        String bookingId,
        Long shipperId,
        String shipperName,
        String bookingStatus,
        String transportStatus,
        String routingStatus,
        String type,
        BigDecimal weightKg,
        Integer quantity,
        String description,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String originUnLocode,
        String originName,
        String destinationUnLocode,
        String destinationName,
        LocalDate departureDate,
        LocalDate arrivalDeadline,
        String hazardousClass,
        String unNumber,
        String properShippingName,
        BigDecimal minCelsius,
        BigDecimal maxCelsius,
        /**
         * 割り当てられた旅程（US09）。経路が決まっていなければ {@code null}。
         *
         * <p>空の配列にしない。「区間が 0 件の旅程がある」と読めてしまい、画面が空の表を出す。
         */
        List<ItineraryLegResponse> itinerary,
        /**
         * 荷主へ通知した日時（US12-4）。通知していなければ {@code null}。
         *
         * <p>画面が「いつ・誰が通知したか」を出すために返す。メールは送っていないため、
         * これが唯一の記録である。
         */
        Instant routeNotifiedAt,
        String routeNotifiedBy,
        /** 発行済みの追跡番号（US14）。未発行なら {@code null}。 */
        String trackingNumber) {

    /**
     * 旅程の区間 1 本。
     *
     * <p>港は<strong>名前まで返す</strong>。UN/LOCODE だけを返すと、画面が 5 文字のコードから
     * 地点名を引き直すことになり、その対応表がフロントとサーバの 2 か所に増える。
     */
    public record ItineraryLegResponse(
            String voyageNumber,
            String loadUnLocode,
            String loadName,
            String unloadUnLocode,
            String unloadName,
            Instant loadTime,
            Instant unloadTime) {
    }

    /** 一覧の 1 件。営業担当者は社名で探すため、結果にも社名を返す。 */
    public static BookingResponse from(CargoSummary summary) {
        return from(summary.cargo(), summary.shipperName());
    }

    public static BookingResponse from(Cargo cargo) {
        return from(cargo, null);
    }

    private static BookingResponse from(Cargo cargo, String shipperName) {
        var specification = cargo.specification();
        var route = cargo.routeSpecification();
        var dimensions = specification.dimensions();

        return new BookingResponse(
                cargo.id(),
                cargo.bookingId().map(BookingId::value).orElse(null),
                cargo.shipperId(),
                shipperName,
                cargo.bookingStatus().name(),
                cargo.transportStatus().name(),
                cargo.routingStatus().name(),
                specification.type().name(),
                specification.weightKg(),
                specification.quantity(),
                specification.description(),
                dimensions == null ? null : dimensions.lengthCm(),
                dimensions == null ? null : dimensions.widthCm(),
                dimensions == null ? null : dimensions.heightCm(),
                route.origin().unLocode(),
                route.origin().name(),
                route.destination().unLocode(),
                route.destination().name(),
                route.departureDate().orElse(null),
                route.arrivalDeadline(),
                cargo.hazardousDeclaration().map(d -> d.hazardousClass().code()).orElse(null),
                cargo.hazardousDeclaration().map(d -> d.unNumber()).orElse(null),
                cargo.hazardousDeclaration().map(d -> d.properShippingName()).orElse(null),
                cargo.temperatureRequirement().map(t -> t.minCelsius()).orElse(null),
                cargo.temperatureRequirement().map(t -> t.maxCelsius()).orElse(null),
                cargo.itinerary().map(BookingResponse::legsOf).orElse(null),
                cargo.routeNotification().map(n -> n.notifiedAt()).orElse(null),
                cargo.routeNotification().map(n -> n.notifiedBy()).orElse(null),
                cargo.trackingNumber().map(t -> t.value()).orElse(null));
    }

    private static List<ItineraryLegResponse> legsOf(CargoItinerary itinerary) {
        return itinerary.legs().stream()
                .map(leg -> new ItineraryLegResponse(
                        leg.voyageNumber().value(),
                        leg.loadLocation().unLocode(), leg.loadLocation().name(),
                        leg.unloadLocation().unLocode(), leg.unloadLocation().name(),
                        leg.loadTime(), leg.unloadTime()))
                .toList();
    }
}
