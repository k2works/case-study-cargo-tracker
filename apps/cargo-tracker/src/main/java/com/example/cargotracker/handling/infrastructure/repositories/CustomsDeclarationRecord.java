package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.Instant;

/** 通関申告の永続化レコード（US29）。 */
public class CustomsDeclarationRecord {

    private long id;
    private long handlingActivityId;
    private String declarationNumber;
    private Instant declaredAt;
    private String status;
    private Instant clearedAt;
    private Instant heldSince;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getHandlingActivityId() {
        return handlingActivityId;
    }

    public void setHandlingActivityId(long handlingActivityId) {
        this.handlingActivityId = handlingActivityId;
    }

    public String getDeclarationNumber() {
        return declarationNumber;
    }

    public void setDeclarationNumber(String declarationNumber) {
        this.declarationNumber = declarationNumber;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(Instant declaredAt) {
        this.declaredAt = declaredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant clearedAt) {
        this.clearedAt = clearedAt;
    }

    public Instant getHeldSince() {
        return heldSince;
    }

    public void setHeldSince(Instant heldSince) {
        this.heldSince = heldSince;
    }
}
