package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.aggregates.Cargo;
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
        String trackingNumber,
        /**
         * いまこの予約に対して行える操作。
         *
         * <p><strong>判定は集約が持つ</strong>（[ADR-021]）。画面が状態名を見比べて同じ
         * 判断を組み立てると、遷移の規則が集約・画面・モックの 3 か所に分かれる。
         *
         * <p><strong>権限は含まない。</strong>ここが答えるのは「予約の状態として行えるか」
         * だけで、「その利用者が行ってよいか」は認可（[ADR-008]）が決める。両方を混ぜると、
         * 状態の規則と職掌の規則が 1 つの値に潰れて、どちらが効いたのか分からなくなる。
         */
        List<BookingAction> availableActions,
        /**
         * 誤配が起きた事実（US28-3・[ADR-026] 決定 3）。起きていなければ {@code null}。
         *
         * <p><strong>状態（{@code routingStatus}）とは別に返す。</strong>再設計して
         * {@code ROUTED} へ戻っても、<strong>この記録は残る</strong>——料金調整の根拠として
         * 参照される。
         *
         * <p><strong>いつ・どこで外れたかまで返す。</strong>「誤配があった」だけでは、
         * 画面は場所を別に問い合わせることになる。
         */
        MisrouteResponse misroute,
        /**
         * 最後に荷役があった港（US28-3・US28-4）。まだ荷役が無ければ {@code null}。
         *
         * <p>誤配のバナーが「いまどこにいるか」を出すために返す。
         * <strong>再設計はここを出発地とする。</strong>
         */
        String lastHandlingLocationUnLocode,
        /**
         * 最後に荷役があった港の名前（IT10 レビュー低 15）。引けなければ {@code null}。
         *
         * <p><strong>符号だけでは画面が対訳表を持つことになる。</strong>この応答は
         * 出発地・目的地・旅程の各区間を「名前（符号）」の形で返しており、誤配のバナーだけ
         * 符号のままだと、担当者はそこで別の表を引く。
         */
        String lastHandlingLocationName,
        /**
         * 到着予定が希望期限を超える日数（US28-6）。超えないなら {@code null}。
         *
         * <p><strong>経路を割り当てた応答でだけ値を持つ。</strong>誤配のあとの再設計で
         * 荷主に伝えるべき差分であり、<strong>「間に合いません」だけでは荷主は次の手を
         * 決められない</strong>。
         *
         * <p>判断は目的地の暦で行う（[ADR-017]）——画面で日付を引き算すると、
         * 利用者の端末の時計と時差の分だけ結果が変わる。
         */
        Long daysBeyondDeadline) {

        public BookingResponse {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        itinerary = itinerary == null ? null : List.copyOf(itinerary);
        availableActions = availableActions == null ? List.of() : List.copyOf(availableActions);
        }


    /**
     * 集約の述語から、行える操作を導く。
     *
     * <p><strong>ここで状態を見比べない。</strong>見比べると、集約の判定とこの一覧が別々に
     * 育ち、応答だけが古い規則を返すようになる。
     */
    private static List<BookingAction> availableActionsOf(Cargo cargo) {
        List<BookingAction> actions = new java.util.ArrayList<>();
        if (cargo.canRequestRouting()) {
            actions.add(BookingAction.REQUEST_ROUTING);
        }
        if (cargo.canAssignItinerary()) {
            actions.add(BookingAction.ASSIGN_ROUTE);
        }
        if (cargo.canRequestConsultation()) {
            actions.add(BookingAction.REQUEST_CONSULTATION);
        }
        if (cargo.canNotifyShipper()) {
            actions.add(BookingAction.NOTIFY_SHIPPER);
        }
        if (cargo.canConfirm()) {
            actions.add(BookingAction.CONFIRM);
        }
        if (cargo.canReturnToRouting()) {
            actions.add(BookingAction.RETURN_TO_ROUTING);
        }
        if (cargo.canIssueTrackingNumber()) {
            actions.add(BookingAction.ISSUE_TRACKING_NUMBER);
        }
        if (cargo.canReviseSchedule()) {
            actions.add(BookingAction.REVISE_SCHEDULE);
        }
        if (cargo.canRequestCancellation()) {
            actions.add(BookingAction.REQUEST_CANCELLATION);
        }
        if (cargo.isMisrouted()) {
            actions.add(BookingAction.REASSIGN_ROUTE);
        }
        return List.copyOf(actions);
    }

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
        return from(summary.cargo(), summary.shipperName(), null, unresolvedLocationNames());
    }

    /**
     * 予約の詳細（US28-6）。
     *
     * <p><strong>超える分は詳細でも読める。</strong>荷主に伝えるのは営業であり、
     * 割り当てた直後の画面にしか出さないと、伝える人の手元に値が残らない。
     */
    public static BookingResponse from(CargoSummary summary, Long daysBeyondDeadline,
            java.util.function.Function<String, java.util.Optional<String>> locationNames) {
        return from(summary.cargo(), summary.shipperName(), daysBeyondDeadline, locationNames);
    }

    public static BookingResponse from(Cargo cargo) {
        return from(cargo, null, null, unresolvedLocationNames());
    }

    /**
     * 港の名前を引けない場面で使う解決関数。
     *
     * <p>誤配の起きていない応答（登録直後など）で使う。<strong>記録が無いので引く相手も
     * いない。</strong>
     */
    private static java.util.function.Function<String, java.util.Optional<String>>
            unresolvedLocationNames() {
        return unLocode -> java.util.Optional.empty();
    }

    /**
     * 経路を割り当てた応答（US28-6）。
     *
     * <p><strong>期限を超えるなら、何日超えるかを添える。</strong>「間に合いません」だけでは、
     * 荷主は次の手を決められない。
     */
    public static BookingResponse from(Cargo cargo, Long daysBeyondDeadline,
            java.util.function.Function<String, java.util.Optional<String>> locationNames) {
        return from(cargo, null, daysBeyondDeadline, locationNames);
    }

    private static BookingResponse from(Cargo cargo, String shipperName,
            Long daysBeyondDeadline,
            java.util.function.Function<String, java.util.Optional<String>> locationNames) {
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
                cargo.trackingNumber().map(t -> t.value()).orElse(null),
                availableActionsOf(cargo),
                cargo.misroute()
                        .map(recorded -> new MisrouteResponse(
                                recorded.at(), recorded.locationUnLocode(),
                                // **記録は名前が引けなくても返す。**誤配は「予定していない港に
                                // 降ろされた」事実であり、その港がマスタに載っている保証はない。
                                // 引けないことを理由に落とすと、最も異常な誤配ほど画面から消える
                                locationNames.apply(recorded.locationUnLocode()).orElse(null)))
                        .orElse(null),
                cargo.lastHandlingLocation().orElse(null),
                cargo.lastHandlingLocation().flatMap(locationNames).orElse(null),
                daysBeyondDeadline);
    }

    /**
     * 誤配が起きた事実（US28-3）。
     *
     * @param at 予定ルート外の荷役が行われた日時
     * @param locationUnLocode その荷役が行われた港
     * @param locationName その港の名前。地点マスタに無ければ {@code null}
     */
    public record MisrouteResponse(Instant at, String locationUnLocode, String locationName) {
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
