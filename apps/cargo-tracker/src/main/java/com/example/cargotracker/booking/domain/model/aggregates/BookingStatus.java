package com.example.cargotracker.booking.domain.model.aggregates;

public enum BookingStatus {
    PRELIMINARY("仮受付", BadgeColor.PRIMARY),
    ROUTE_PROPOSED("経路提案済", "info"),
    CONFIRMED("予約確定", "success"),
    TRACKING_ISSUED("追跡番号発行済", BadgeColor.PRIMARY),
    IN_TRANSIT("輸送中", BadgeColor.PRIMARY),
    DELIVERED("配達完了", "success"),
    SETTLED("精算完了", "secondary"),
    CANCELLED("キャンセル", "secondary");

    private final String displayName;
    private final String badgeColor;

    BookingStatus(String displayName, String badgeColor) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    BookingStatus(String displayName, BadgeColor badgeColor) {
        this(displayName, badgeColor.value);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    private enum BadgeColor {
        PRIMARY("primary");

        private final String value;

        BadgeColor(String value) {
            this.value = value;
        }
    }
}
