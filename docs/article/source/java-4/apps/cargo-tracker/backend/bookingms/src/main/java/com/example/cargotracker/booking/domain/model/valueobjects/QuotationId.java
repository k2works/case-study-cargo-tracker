package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 見積の識別子（US01 §受入基準 4「見積番号が発行される」）。
 *
 * <p><b>人が読める形にする。</b> 営業担当者と荷主は見積番号で会話するので、
 * 素の UUID だと画面でも問い合わせでも扱えない。</p>
 *
 * <p><b>列の長さに収める。</b> {@code quotation.quotation_id} は
 * {@code VARCHAR(36)} である。接頭辞 + UUID をそのまま繋ぐと 40 文字になり、
 * <b>集約は受け付けるのに投影だけが退避される</b>（IT13 の請求書 ID・IT14 の
 * 調整 ID と同じ形で、2 度踏んだ）。ハイフンを外して 4 + 8 + 1 + 22 に収める。</p>
 */
public record QuotationId(String value) {

    /** 列の長さ。ここを超えると投影だけが静かに落ちる。 */
    public static final int MAX_LENGTH = 36;

    public QuotationId {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("見積 ID は必須です");
        }
        if (value.length() > MAX_LENGTH) {
            throw new BusinessRuleViolation(
                    "見積 ID が長すぎます（" + MAX_LENGTH + " 文字まで）: " + value);
        }
    }
}
