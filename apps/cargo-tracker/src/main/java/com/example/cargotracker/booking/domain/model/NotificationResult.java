package com.example.cargotracker.booking.domain.model;

/**
 * 通知の送信結果（US12）。
 *
 * <p><strong>失敗も記録する。</strong> 失敗を残さないと「送ったが届かなかった」を
 * 後から追えない。「送ったつもり」を検知することが US12 の目的である。
 */
public enum NotificationResult {

    SUCCEEDED("成功", "bg-success"),
    FAILED("失敗", "bg-danger");

    private final String displayName;
    private final String badgeClass;

    NotificationResult(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String displayName() {
        return displayName;
    }

    public String badgeClass() {
        return badgeClass;
    }

    /** 再送の対象か。**失敗したものだけ再送できる。** */
    public boolean resendable() {
        return this == FAILED;
    }
}
