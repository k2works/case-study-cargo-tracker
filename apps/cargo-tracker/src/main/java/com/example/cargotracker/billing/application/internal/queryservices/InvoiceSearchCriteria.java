package com.example.cargotracker.billing.application.internal.queryservices;

import com.example.cargotracker.billing.domain.model.ChargeStatus;
import com.example.cargotracker.billing.domain.model.PaymentStatus;
import java.time.LocalDate;

/**
 * 請求書一覧の絞り込み条件（IT14 レビュー C1 / C2）。
 *
 * <p><strong>月次の締めは期間で切る。</strong> 発行日で絞れないと、経理担当者は
 * 全件を目で追って先月分を拾うことになる。荷主で絞れないと、
 * 「この会社の先月の請求はいくらか」に答えられない。
 *
 * <p><strong>「発行待ち」は 2 つの軸にまたがる</strong>（ADR-017）。料金は確定したのに
 * 発行していない請求書は、{@code charge_status} でも {@code payment_status} でも
 * 選び出せない。<strong>確定したまま忘れられた請求書は、誰も請求しないまま
 * 月をまたぐ</strong>ため、業務の言葉のほうを 1 つの選択肢にする。
 *
 * <p><strong>画面に列名を判断させない。</strong> 画面が渡すのは業務の語であり、
 * それがどちらの軸かはここで決める。
 *
 * @param chargeStatus  料金の状態。{@code null} なら絞らない
 * @param paymentStatus 支払いの状態。{@code null} なら絞らない
 * @param awaitingIssue <strong>確定したまま未発行だけ</strong>に絞るか
 * @param issuedFrom    発行日の下限（当日を含む）。{@code null} なら絞らない
 * @param issuedTo      発行日の上限（<strong>当日を含む</strong>）。{@code null} なら絞らない
 * @param shipperName   荷主名（部分一致・大小文字を問わない）。空なら絞らない
 */
public record InvoiceSearchCriteria(
        String chargeStatus,
        String paymentStatus,
        boolean awaitingIssue,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        String shipperName) {

    /** 「確定したまま未発行」を表す画面の語（C2）。 */
    public static final String AWAITING_ISSUE = "AWAITING_ISSUE";

    /** 何も絞らない。 */
    public static InvoiceSearchCriteria all() {
        return new InvoiceSearchCriteria(null, null, false, null, null, null);
    }

    /**
     * 画面の入力から組み立てる。
     *
     * <p><strong>両方の軸にある語は料金の軸として読む。</strong> {@code CONFIRMED} は
     * 「料金が確定」と「入金確認済」の両方に存在する。画面の「確定」は
     * US21 から料金の意味で使ってきたため、そちらを変えない。
     *
     * <p><strong>知らない語は料金の軸として扱う</strong>（既存の動き）。
     *
     * @param status 画面が選んだ状態。空なら絞らない
     */
    public static InvoiceSearchCriteria of(
            String status, LocalDate issuedFrom, LocalDate issuedTo, String shipperName) {
        String normalized = blankToNull(status);
        if (AWAITING_ISSUE.equals(normalized)) {
            return new InvoiceSearchCriteria(
                    ChargeStatus.CONFIRMED.name(), null, true,
                    issuedFrom, issuedTo, blankToNull(shipperName));
        }
        boolean payment = normalized != null && isPaymentAxis(normalized);
        return new InvoiceSearchCriteria(
                payment ? null : normalized,
                payment ? normalized : null,
                false,
                issuedFrom, issuedTo, blankToNull(shipperName));
    }

    /** 期間・荷主・発行待ちのいずれかで絞っているか（<strong>画面の案内に使う</strong>）。 */
    public boolean narrowed() {
        return chargeStatus != null || paymentStatus != null || awaitingIssue
                || issuedFrom != null || issuedTo != null || shipperName != null;
    }

    private static boolean isPaymentAxis(String status) {
        for (ChargeStatus charge : ChargeStatus.values()) {
            if (charge.name().equals(status)) {
                return false;
            }
        }
        for (PaymentStatus value : PaymentStatus.values()) {
            if (value.name().equals(status)) {
                return true;
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
