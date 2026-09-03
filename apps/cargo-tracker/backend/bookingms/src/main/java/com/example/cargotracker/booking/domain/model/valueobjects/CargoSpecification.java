package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 貨物の仕様（domain-model.md「Cargo 集約の不変条件」3）。
 *
 * <p>危険物なら申告、冷凍・冷蔵なら温度条件を必ず添える。ここで検査するのは、
 * 種別ごとの要件が「登録の時点で」満たされていなければ、経路設計（US08）が
 * 対応可能な航海を絞れないため。あとから足すと、既に受け付けた予約が要件を
 * 満たさないまま残る。</p>
 */
public record CargoSpecification(
        CargoType cargoType,
        Weight weight,
        Dimensions dimensions,
        int quantity,
        String productName,
        HazardousDeclaration hazardousDeclaration,
        TemperatureRequirement temperatureRequirement) {

    public CargoSpecification {
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weight == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        if (dimensions == null) {
            throw new IllegalArgumentException("寸法は必須です");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量は 1 以上です: " + quantity);
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("品名は必須です");
        }
        if (cargoType == CargoType.HAZARDOUS && hazardousDeclaration == null) {
            throw new IllegalArgumentException("危険物には危険物申告が必要です");
        }
        if (cargoType == CargoType.REFRIGERATED && temperatureRequirement == null) {
            throw new IllegalArgumentException("冷凍・冷蔵貨物には温度管理条件が必要です");
        }
        // 種別と付帯情報の食い違いも断る。一般貨物に危険物申告が付いていると、
        // 経路設計が「危険物対応の航海だけ」に絞るべきか判断できない。
        if (cargoType != CargoType.HAZARDOUS && hazardousDeclaration != null) {
            throw new IllegalArgumentException("危険物以外に危険物申告は付けられません");
        }
        if (cargoType != CargoType.REFRIGERATED && temperatureRequirement != null) {
            throw new IllegalArgumentException("冷凍・冷蔵以外に温度管理条件は付けられません");
        }
    }
}
