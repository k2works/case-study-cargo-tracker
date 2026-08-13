package com.example.cargotracker.estimation.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * ルート候補（US01 の受入基準 3）。
 *
 * <p><strong>Routing の探索から得た値をそのまま持つ</strong>（ADR-023）。
 * 見積は作成時の候補を保存し、あとで作り直さない —— <strong>先週の見積と
 * 今日の見積で数字が違っては、荷主に伝えた内容を説明できない</strong>。
 *
 * @param voyageNumber  航海番号。<strong>実在する便である</strong>
 * @param transitPort   経由港。直行なら {@code null}
 * @param transitDays   所要日数
 * @param estimatedCost 概算費用（ADR-008。<strong>概算であり実額ではない</strong>）
 * @param currency      通貨
 */
public record RouteCandidate(
        String voyageNumber,
        String transitPort,
        int transitDays,
        BigDecimal estimatedCost,
        String currency) {

    public RouteCandidate {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        if (transitDays <= 0) {
            throw new IllegalArgumentException("所要日数は正の値です: " + transitDays);
        }
        if (estimatedCost == null || estimatedCost.signum() <= 0) {
            throw new IllegalArgumentException("概算費用は正の値です: " + estimatedCost);
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("通貨は必須です");
        }
    }

    /** 直行か。<strong>経路設計で最初に見る情報である。</strong> */
    public boolean isDirect() {
        return transitPort == null || transitPort.isBlank();
    }
}
