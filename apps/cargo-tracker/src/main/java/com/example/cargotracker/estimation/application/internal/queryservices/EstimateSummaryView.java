package com.example.cargotracker.estimation.application.internal.queryservices;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積一覧の 1 行（US01）。
 *
 * <p><strong>営業担当者が探すのは「いつ・どこからどこへ・いくら」である。</strong>
 * 見積 ID だけでは、どの案件の見積か思い出せない。
 *
 * @param estimateId 見積番号（受入基準 4）
 * @param route      出発地と目的地
 * @param cargo      貨物の仕様
 * @param deadline   希望到着期限
 * @param cheapest   最安候補の概算費用。候補が無ければ {@code null}
 * @param status     見積の状態
 * @param createdOn  作成日（業務のタイムゾーン）
 */
public record EstimateSummaryView(
        String estimateId,
        Route route,
        Cargo cargo,
        LocalDate deadline,
        BigDecimal cheapest,
        Status status,
        LocalDate createdOn) {

    /**
     * 経路。
     *
     * @param origin      出発地 UN/LOCODE
     * @param destination 目的地 UN/LOCODE
     */
    public record Route(String origin, String destination) { }

    /**
     * 貨物の仕様。
     *
     * @param typeLabel 貨物種別の表示名
     * @param weightKg  重量（kg）
     */
    public record Cargo(String typeLabel, BigDecimal weightKg) { }

    /**
     * 見積の状態。
     *
     * @param label 表示名
     * @param badge バッジ用 Bootstrap クラス
     * @param expired 期限切れか
     */
    public record Status(String label, String badge, boolean expired) { }

    /** 概算費用を出せるか。<strong>候補が無い見積もある</strong>（便が無い場合）。 */
    public boolean hasCheapest() {
        return cheapest != null;
    }
}
