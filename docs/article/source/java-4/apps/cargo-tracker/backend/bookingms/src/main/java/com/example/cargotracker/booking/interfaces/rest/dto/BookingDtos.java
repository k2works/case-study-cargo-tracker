package com.example.cargotracker.booking.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 貨物予約の入出力（UI 設計 S21）。 */
public final class BookingDtos {

    private BookingDtos() {
    }

    /**
     * 貨物仕様と輸送条件の入力。受付（US04）と修正（US32）で同じ形にする。
     *
     * <p>別々に持つと、片方にだけ項目を足したときに「登録では入れられるのに
     * 修正では消える」が生まれる。</p>
     */
    public interface CargoFields {
        String originUnLocode();

        String destinationUnLocode();

        LocalDate arrivalDeadline();

        String cargoType();

        BigDecimal weightKg();

        BigDecimal lengthCm();

        BigDecimal widthCm();

        BigDecimal heightCm();

        Integer quantity();

        String productName();

        String hazardImoClass();

        String hazardUnNumber();

        BigDecimal temperatureMinC();

        BigDecimal temperatureMaxC();
    }

    /**
     * 修正の入力（US32）。
     *
     * <p>予約 ID は経路が持つ。荷主は変えられない（不変条件 1）。荷主を間違えたなら、
     * それは別の予約である。</p>
     */
    public record UpdateBookingRequest(
            @NotBlank String originUnLocode,
            @NotBlank String destinationUnLocode,
            @NotNull LocalDate arrivalDeadline,
            @NotBlank String cargoType,
            @NotNull BigDecimal weightKg,
            @NotNull BigDecimal lengthCm,
            @NotNull BigDecimal widthCm,
            @NotNull BigDecimal heightCm,
            @NotNull Integer quantity,
            @NotBlank String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC) implements CargoFields {
    }

    /**
     * 予約の登録。
     *
     * <p>到着期限は日付で受ける。時刻付きにすると、当日着を「間に合わない」と
     * 判定する経路ができる（不変条件 5）。</p>
     *
     * <p>種別ごとの必須項目（危険物申告・温度条件）はここでは検査しない。判断は
     * 集約が持ち、入口は形だけを見る。両方に置くと、集約を直したときに入口だけが
     * 古い規則で弾く。</p>
     */
    public record BookCargoRequest(
            @NotBlank String shipperId,
            @NotBlank String originUnLocode,
            @NotBlank String destinationUnLocode,
            @NotNull LocalDate arrivalDeadline,
            @NotBlank String cargoType,
            @NotNull BigDecimal weightKg,
            @NotNull BigDecimal lengthCm,
            @NotNull BigDecimal widthCm,
            @NotNull BigDecimal heightCm,
            @NotNull Integer quantity,
            @NotBlank String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC,
            // 見積番号（任意・US01）。**見積を経ない予約もある**ので必須にしない。
            // 入っていれば、見積と違う項目を項目名で知らせる（正典の不変条件 3）。
            String quotationId) implements CargoFields {
    }

    /**
     * 経路候補の応答（US08）。
     *
     * <p><b>費用の欄を持たない。</b> 料金の式と料率は IT13（US21）で入ったが、
     * <b>それは請求のもの</b>——実際に通った区間で数える。候補経路の概算を出すのは
     * <b>見積（US01・IT14）</b>で、正典もそう書いている（{@code domain-model.md}
     * 「Booking の Quotation はこの式と同じ料率で概算を出す」）。料率を bookingms へ
     * 写すと出典が 2 つになる（[ADR-0016] 決定 1 に反する）。0 を返すと「費用 0 円の
     * 経路」と読めるので、<b>US08 §受入基準 3 は US01 と同じ IT で満たす</b>。</p>
     *
     * @param truncated 探索の上限で切ったか（ADR-0007）。0 件と言い分けるために出す
     */
    public record RouteCandidatesResponse(
            java.util.List<RouteCandidateResponse> candidates,
            boolean truncated) {
    }

    /** 経路候補 1 件。区間の順序が業務の意味を持つ。 */
    public record RouteCandidateResponse(
            java.util.List<LegResponse> legs, int transitDays, boolean direct,
            /*
             * 到着期限を何日超えるか（US28 §受入基準 6）。通常の設計では必ず 0。
             * **誤配の再設計でだけ 0 より大きくなる**——候補を隠すと 0 件になり、
             * 貨物が動かせなくなるので、超過した候補も出して日数を添える。
             */
            int overdueDays) {

        /** 区間 1 つ。航海番号を出す（US08 §受入基準 3）。 */
        public record LegResponse(
                String voyageNumber,
                String loadUnLocode,
                String unloadUnLocode,
                java.time.Instant loadTime,
                java.time.Instant unloadTime) {
        }
    }

    /**
     * 経路の確定（US09）。
     *
     * <p><b>候補 ID ではなく旅程そのものを送る。</b> 経路候補はテーブルに持たないので、
     * 選んでから送るまでの間に航海が更新されうる。</p>
     */
    public record AssignRouteRequest(
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid
            java.util.List<LegRequest> legs) {

        public record LegRequest(
                @jakarta.validation.constraints.NotBlank String voyageNumber,
                @jakarta.validation.constraints.NotBlank String loadUnLocode,
                @jakarta.validation.constraints.NotBlank String unloadUnLocode,
                @jakarta.validation.constraints.NotNull java.time.Instant loadTime,
                @jakarta.validation.constraints.NotNull java.time.Instant unloadTime) {
        }
    }

    /**
     * 経路条件の調整（US10）。
     *
     * <p><b>貨物種別を持たない。</b> 種別を変えるのは「その貨物が何か」を変えることで、
     * 危険物申告や温度条件が付いて回る。経路を探す条件ではないので US32 が持つ。</p>
     */
    public record AdjustRouteSpecificationRequest(
            @jakarta.validation.constraints.NotNull java.time.LocalDate arrivalDeadline,
            java.util.List<String> excludeUnLocodes,
            String departFromUnLocode) {
    }

    /**
     * 条件の見直し依頼（US10 §受入基準 4）。
     *
     * <p>理由は必須。無いと営業は荷主と何を協議すればよいのか分からない。</p>
     */
    public record RequestConditionReviewRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 200) String reason) {
    }

    /**
     * 荷主との協議の結果（US10 §受入基準 4 の対）。
     *
     * <p>中身が無いと、経路設計者は条件をどう直せばよいのか分からない。</p>
     */
    public record RespondToConditionReviewRequest(
            @jakarta.validation.constraints.NotBlank String response) {
    }

    /**
     * 荷主への通知（US12）。
     *
     * <p><b>料金概算の欄を持たない。</b> 荷主へ知らせるのは<b>見積の概算</b>で、
     * それを出すのは US01（IT14）である。IT13 で入った料金は請求のもの（実際に
     * 通った区間で数える）で、経路を知らせる時点ではまだ通っていない。0 を出すと
     * 「費用 0 円」と読める（US12 §受入基準 2 は US01 と同じ IT で満たす）。</p>
     */
    public record NotifyShipperRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 255) String recipientEmail,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 500) String summary) {
    }

    /** 経路設計へ戻す（US12）。理由は必須（経路設計者が何を直すか分からない）。 */
    public record ReturnToRoutingRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 200) String reason) {
    }

    /** 確定した旅程（S22 / US09）。並び順が業務の意味を持つ。 */
    public record ItineraryResponse(java.util.List<ItineraryLegResponse> legs) {
    }

    public record ItineraryLegResponse(
            int legSeq,
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            java.time.Instant loadAt,
            java.time.Instant unloadAt) {
    }

    /** その航海で経路を組んだ予約（S34 / US24）。件数は items の長さで足りる。 */
    public record AffectedBookingsResponse(java.util.List<AffectedBookingResponse> items) {
    }

    public record AffectedBookingResponse(
            String bookingId,
            String bookingNumber,
            String bookingStatus,
            String routingStatus) {
    }

    /**
     * 予約を受け付けた応答。
     *
     * @param quotationDifferences 見積と違った項目（「何から何へ」まで）。
     *     <b>断らずに知らせる</b>（正典の不変条件 3）——荷主の事情は見積のあとで
     *     変わるので、断ると業務が止まる。見積を経ない予約では<b>空</b>
     */
    public record BookCargoResponse(String bookingId, java.util.List<String> quotationDifferences) {

        /** 見積を経ない予約。 */
        public BookCargoResponse(String bookingId) {
            this(bookingId, java.util.List.of());
        }
    }
}
