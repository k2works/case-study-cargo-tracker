package com.example.cargotracker.booking.domain.model.aggregates;

public enum BookingStatus {
    PRELIMINARY("仮受付", "warning"),
    ROUTE_PROPOSED("経路提案済", "info"),
    CONFIRMED("予約確定", "success"),
    TRACKING_ISSUED("追跡番号発行済", "primary"),
    IN_TRANSIT("輸送中", "primary"),
    DELIVERED("配達完了", "success"),
    SETTLED("精算完了", "secondary"),
    CANCELLED("キャンセル", "danger");

    private final String displayName;
    private final String badgeColor;

    BookingStatus(String displayName, String badgeColor) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }
}
