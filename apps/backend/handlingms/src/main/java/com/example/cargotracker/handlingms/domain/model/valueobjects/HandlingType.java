package com.example.cargotracker.handlingms.domain.model.valueobjects;

/**
 * 荷役作業の種別。
 *
 * <p>domain-model.md L807-813 準拠。CLAIM は引取（旧 PICKUP）、CUSTOMS は税関通過。</p>
 *
 * <ul>
 *   <li>{@link #RECEIVE} - 受領（出発港で貨物を受け取る）</li>
 *   <li>{@link #LOAD} - 積込（船舶への積込、voyageNumber 必須）</li>
 *   <li>{@link #UNLOAD} - 荷降し（船舶からの荷降し、voyageNumber 必須）</li>
 *   <li>{@link #CLAIM} - 引取（荷受人への引き渡し、ClaimVerification 必須・US16）</li>
 *   <li>{@link #CUSTOMS} - 税関通過</li>
 * </ul>
 */
public enum HandlingType {
    RECEIVE,
    LOAD,
    UNLOAD,
    CLAIM,
    CUSTOMS;

    /** LOAD / UNLOAD は voyageNumber が必須となる種別。 */
    public boolean requiresVoyageNumber() {
        return this == LOAD || this == UNLOAD;
    }

    /** CLAIM は ClaimVerification が必須となる種別（US16）。 */
    public boolean requiresClaimVerification() {
        return this == CLAIM;
    }
}
