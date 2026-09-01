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
            boolean registerAnyway,
            /** シミュレーション由来として登録する（[ADR-030] 決定 3）。荷主コードの帯が変わる。 */
            boolean simulated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShipperResponse(Long id, String shipperCode) {
    }

    /**
     * 予約の登録。
     *
     * <p><strong>貨物種別によって要る項目が変わる</strong>——冷凍・冷蔵は保管温度、
     * 危険物は危険物申告が要る。添えないと集約が断る（実環境で 23 件落ちた）。
     * 断られること自体は正しい振る舞いであり、こちらの入力が足りていない。
     */
    record BookingRequest(Long shipperId, String type, Integer weightKg, String description,
            String originUnLocode, String destinationUnLocode, String arrivalDeadline,
            String hazardousClass, String unNumber, String properShippingName,
            java.math.BigDecimal minCelsius, java.math.BigDecimal maxCelsius) {
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

    /** 例外を起票する（US19-1・US20-1）。 */
    record RaiseExceptionRequest(String trackingNumber, String exceptionType,
            String description) {
    }

    /** 例外を解決する（US19-4）。 */
    record ResolveExceptionRequest(String trackingNumber, Long exceptionId,
            String resolutionNotes, String newEstimatedArrival) {
    }

    /** 起きている例外を読むための応答。解決に要る番号を取る。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ManagedTrackingResponse(String trackingNumber, RaisedIssue activeException) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        /**
         * 起きている例外。
         *
         * <p>型の名前を {@code Exception} で終わらせない。Java では例外クラスの
         * 名前であり、読む人を惑わせる——ここでの「例外」は業務の言葉である。
         */
        record RaisedIssue(Long id, String exceptionType) {
        }
    }

    /** キャンセルを申請する（US30-1）。 */
    record RequestCancellationRequest(String reason) {
    }

    /** キャンセルを承認する（US30-5）。**陸揚げ地は必須**（[ADR-025] 決定 4）。 */
    record ApproveCancellationRequest(String dischargeLocationUnLocode, String decisionReason) {
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

    /** 利用者と荷主の紐付け（US33 の管理者操作・US39 で使う）。 */
    public record UserShipperLinkRequest(Long shipperId) {
    }

    /** 利用者と荷主の紐付けの応答（紐付いていなければ {@code shipperId} は空）。 */
    public record UserShipperLinkResponse(String username, Long shipperId) {
    }
}
