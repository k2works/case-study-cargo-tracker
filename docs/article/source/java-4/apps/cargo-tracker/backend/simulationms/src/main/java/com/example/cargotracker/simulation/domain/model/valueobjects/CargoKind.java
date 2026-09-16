package com.example.cargotracker.simulation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 予約に載せる貨物の種別と、その種別に要る付帯情報（US33 §受入基準 1）。
 *
 * <p><b>種別と付帯情報を 1 か所に持つ。</b> 業務は種別ごとに付帯情報を
 * <b>両方向に</b>検査する——危険物には危険物申告が要り、危険物以外には付けられない
 * （`CargoSpecification`）。種別の名簿と付帯情報を別々に持つと、種別を足した人が
 * 付帯情報を足し忘れ、<b>そのシナリオだけが 422 で止まる</b>
 * （実際に危険物と冷凍・冷蔵で起きた）。</p>
 *
 * <p><b>値は固定する</b>（乱数で決めない）。同じ種から同じ並びを作れること
 * （US36 §受入基準 3）を、項目を増やすたびに難しくしない——確かめたいのは
 * 業務の連鎖であって、申告の中身ではない。</p>
 *
 * <p><b>呼び名は業務の列挙と同じ語</b>（`CargoType`）。境界で翻訳しない。</p>
 */
public enum CargoKind {

    /** 一般。付帯情報は無い。 */
    GENERAL(Map.of()),

    /** 危険物。**危険物申告が要る**（無いと業務が断る）。 */
    HAZARDOUS(Map.of("hazardImoClass", "3", "hazardUnNumber", "UN1203")),

    /** 冷凍・冷蔵。**温度管理条件が要る**（無いと業務が断る）。 */
    REFRIGERATED(Map.of("temperatureMinC", "-20", "temperatureMaxC", "-5"));

    private final Map<String, Object> declaration;

    CargoKind(Map<String, Object> declaration) {
        this.declaration = declaration;
    }

    /**
     * 予約の要求に足す付帯情報。
     *
     * <p><b>その種別に要るものだけを返す。</b> 他の種別のものを混ぜると、
     * 「危険物以外に危険物申告は付けられません」で断られる。</p>
     */
    public Map<String, Object> declaration() {
        return new LinkedHashMap<>(declaration);
    }

    /** 呼び名から引く。<b>知らない語は断る</b>——打ち間違いを 422 に化けさせない。 */
    public static CargoKind of(String name) {
        for (CargoKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        throw new BusinessRuleViolation("知らない貨物種別です: " + name);
    }
}
