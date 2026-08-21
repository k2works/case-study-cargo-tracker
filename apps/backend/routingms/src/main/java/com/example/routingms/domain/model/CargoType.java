package com.example.routingms.domain.model;

/**
 * 航海が運べる貨物種別。
 *
 * <p>Booking Context にも同名の列挙型があるが、<strong>別の型として定義する</strong>
 * （共有カーネルに引き上げない）。予約側は「その貨物が何か」を、経路側は「その船が何を運べるか」を
 * 表しており、意味が違う。片方に値が増えたとき、もう片方が必ず追随するとは限らない。
 * 共有カーネルにすると、その「必ず追随する」という誤った前提を構造で固定してしまう。
 */
public enum CargoType {
    GENERAL("一般貨物"),
    HAZARDOUS("危険物"),
    REFRIGERATED("冷凍・冷蔵");

    private final String label;

    CargoType(String label) {
        this.label = label;
    }

    /** 画面と差分に出す言葉。列挙名をそのまま出すと、運航管理者は照合できない。 */
    public String label() {
        return label;
    }
}
