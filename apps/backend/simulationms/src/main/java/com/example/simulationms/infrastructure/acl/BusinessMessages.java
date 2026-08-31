package com.example.simulationms.infrastructure.acl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * 業務 API と取り交わす形（[ADR-030] 決定 2）。
 *
 * <p><strong>相手の型は持ち込まない。</strong>各サービスの record を import すると、
 * シミュレーションが業務の 6 サービスに型で依存する——相手が項目を足しただけで
 * こちらが動かなくなる。ここに写しを置き、<strong>知らない項目は無視する</strong>。
 *
 * <p>出口の本体（{@link RestBusinessGateway}）から分けているのは、呼び出しの手順と
 * 取り交わす形は変わる理由が違うためである。
 */
final class BusinessMessages {

    private BusinessMessages() {
    }

    record LoginRequest(String userId, String password) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(String token) {
    }

    /** 荷主登録の依頼。相手の型は持ち込まない。 */
    record ShipperRequest(String type, String name, String email, String address, String phone,
            boolean registerAnyway) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShipperResponse(Long id, String shipperCode) {
    }

    record BookingRequest(Long shipperId, String type, Integer weightKg, String description,
            String originUnLocode, String destinationUnLocode, String arrivalDeadline) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookingResponse(String bookingId, String trackingNumber) {
    }

    record VoyageRequest(String voyageNumber, String vesselName, String carrierName,
            List<String> supportedCargoTypes, List<MovementRequest> movements) {

        record MovementRequest(String departureUnLocode, String arrivalUnLocode,
                Instant departureTime, Instant arrivalTime) {
        }
    }

    record AssignRouteRequest(List<LegRequest> legs, Integer maxTransshipments) {
    }

    record LegRequest(String voyageNumber, String loadUnLocode, String unloadUnLocode,
            String loadTime, String unloadTime) {
    }


    /** 荷役の記録。日時は文字列で送る（相手が文字列で受ける）。 */
    record HandlingActivityRequest(String trackingNumber, String type, String locationUnLocode,
            String completionTime, String operatorName, String voyageNumber,
            String consigneeConfirmation) {
    }

    record CustomsDeclarationRequest(String trackingNumber, String declarationNumber,
            String declaredAt, String remarks) {
    }

    record CustomsStatusRequest(String status, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomsDeclarationResponse(Long declarationId) {
    }

    /** 料金の調整は入れない。実演で見たいのは通ることであり、調整の妥当性ではない。 */
    record CalculateRequest(List<String> adjustments) {
    }

    record ConfirmPaymentRequest(java.math.BigDecimal amountValue, String paidAt, String method,
            String transactionReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InvoiceResponse(String invoiceNumber, MoneyResponse totalAmount) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record MoneyResponse(java.math.BigDecimal value, String currency) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteCandidateListResponse(List<Candidate> candidates) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidate(List<Leg> legs) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Leg(String voyageNumber, String fromUnLocode, String toUnLocode,
                String departureTime, String arrivalTime) {
        }
    }
}
