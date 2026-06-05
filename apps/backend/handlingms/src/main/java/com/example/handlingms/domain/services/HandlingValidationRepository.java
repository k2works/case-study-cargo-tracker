package com.example.handlingms.domain.services;

import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.CargoSnapshot;

import java.time.LocalDateTime;

/**
 * {@link HandlingValidationService} が必要とする Read Model アクセスのポート
 * （IT8 H2 持ち越し T1.11、ADR-0014 / Onion Architecture / DIP 回復）。
 *
 * <p>domain 層から infrastructure 層への逆向き依存（DIP 違反）を解消するため、
 * domain 層側で必要な振る舞いを interface として定義する。実装は infrastructure 層
 * （{@code MybatisHandlingValidationRepository}）に置く。</p>
 *
 * <p>IT7 までは domain → infrastructure の直接参照だったが、ArchUnit DSL 化（IT8 T1.1）で
 * 除外項目として顕在化した。</p>
 */
public interface HandlingValidationRepository {

    /**
     * 重複登録判定用：同一 trackingNumber + handlingType + unlocode + 指定範囲の occurredAt
     * のイベント件数を返す。
     *
     * @param trackingNumber 追跡番号
     * @param handlingType   荷役種別
     * @param unlocode       発生地 UN/LOCODE
     * @param windowStart    範囲開始（inclusive）
     * @param windowEnd      範囲終了（inclusive）
     * @return 該当件数
     */
    long countDuplicates(String trackingNumber, HandlingType handlingType,
                         String unlocode,
                         LocalDateTime windowStart, LocalDateTime windowEnd);

    /**
     * 予定外検知用：CargoSnapshot を trackingNumber で取得する。
     *
     * @return 未到着なら {@code null}
     */
    CargoSnapshot findCargoSnapshotByTrackingNumber(String trackingNumber);
}
