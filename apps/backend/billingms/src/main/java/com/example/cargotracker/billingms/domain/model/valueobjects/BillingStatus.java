package com.example.cargotracker.billingms.domain.model.valueobjects;

/**
 * 請求の状態を表す列挙型（US21-US23）。
 *
 * <pre>
 * PENDING → CALCULATED → INVOICED → PAID
 *                                  → OVERDUE
 *         → CANCELLED
 * </pre>
 */
public enum BillingStatus {
    PENDING,
    CALCULATED,
    INVOICED,
    PAID,
    OVERDUE,
    CANCELLED
}
