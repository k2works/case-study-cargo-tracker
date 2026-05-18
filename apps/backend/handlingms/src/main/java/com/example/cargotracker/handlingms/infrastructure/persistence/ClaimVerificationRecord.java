package com.example.cargotracker.handlingms.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * claim_verification テーブルの Read Model レコード（US16）。
 */
public class ClaimVerificationRecord {

    private String activityId;
    private String consigneeName;
    private String signatureRef;
    private String confirmationCode;
    private LocalDateTime verifiedAt;

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getConsigneeName() {
        return consigneeName;
    }

    public void setConsigneeName(String consigneeName) {
        this.consigneeName = consigneeName;
    }

    public String getSignatureRef() {
        return signatureRef;
    }

    public void setSignatureRef(String signatureRef) {
        this.signatureRef = signatureRef;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
