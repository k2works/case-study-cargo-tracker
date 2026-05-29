package com.example.trackingms.domain.services;

import com.example.trackingms.domain.model.TransportStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.example.trackingms.domain.model.TransportStatus.AWAITING_CLAIM;
import static com.example.trackingms.domain.model.TransportStatus.DELIVERED;
import static com.example.trackingms.domain.model.TransportStatus.EXCEPTION;
import static com.example.trackingms.domain.model.TransportStatus.IN_TRANSIT;
import static com.example.trackingms.domain.model.TransportStatus.LOADED;
import static com.example.trackingms.domain.model.TransportStatus.MISROUTED;
import static com.example.trackingms.domain.model.TransportStatus.NOT_RECEIVED;
import static com.example.trackingms.domain.model.TransportStatus.RECEIVED;
import static com.example.trackingms.domain.model.TransportStatus.UNLOADED;

/**
 * 輸送状態遷移ガード（domain-model.md / iteration_plan-5.md US17 / IT5 タスク 2.1）。
 *
 * <p>{@link TransportStatus} 9 値間の遷移のうち、業務的に許可されたものだけを通すドメインサービス。
 * {@code TrackingActivity} 集約の状態更新コマンドハンドラから呼び出され、不正遷移を構造的に防ぐ。</p>
 *
 * <p>許可遷移は iteration_plan-5.md「輸送状態の遷移（US17 範囲）」と
 * domain-model.md「TransportStatus 状態遷移」に準拠する。</p>
 *
 * <h2>許可遷移マトリックス</h2>
 * <ul>
 *   <li>前進：NOT_RECEIVED→RECEIVED→LOADED→IN_TRANSIT→UNLOADED→AWAITING_CLAIM→DELIVERED</li>
 *   <li>中継港の積み替え：UNLOADED→LOADED</li>
 *   <li>誤配送検知：{NOT_RECEIVED, RECEIVED, LOADED, IN_TRANSIT, UNLOADED}→MISROUTED</li>
 *   <li>例外発生：{NOT_RECEIVED..AWAITING_CLAIM}→EXCEPTION</li>
 *   <li>例外復帰：EXCEPTION→{RECEIVED, LOADED, IN_TRANSIT}</li>
 * </ul>
 *
 * <h2>拒否方針</h2>
 * <ul>
 *   <li>同一状態への遷移は拒否（更新の意味がない）</li>
 *   <li>終端 DELIVERED からの遷移は拒否（配送完了は不可逆）</li>
 *   <li>MISROUTED からの復帰は本 IT 範囲外</li>
 *   <li>逆行・スキップは拒否</li>
 * </ul>
 */
@Component
public class TransportStatusTransition {

    /**
     * 遷移許可マトリックス（from → 許可される to の集合）。
     *
     * <p>MISROUTED は IT5 リリース時点では終端扱いだったが、業務的には「誤配送」は
     * 「終了」ではなく「異常」状態であるためレビュー H5 で救済動線を追加。
     * MISROUTED → RECEIVED / LOADED / IN_TRANSIT は追跡管理者の手動更新でのみ許可する
     * （再経路設計や緊急輸送による正常状態への復帰）。</p>
     */
    private static final Map<TransportStatus, Set<TransportStatus>> ALLOWED = Map.ofEntries(
            Map.entry(NOT_RECEIVED, Set.of(RECEIVED, MISROUTED, EXCEPTION)),
            Map.entry(RECEIVED, Set.of(LOADED, MISROUTED, EXCEPTION)),
            Map.entry(LOADED, Set.of(IN_TRANSIT, MISROUTED, EXCEPTION)),
            Map.entry(IN_TRANSIT, Set.of(UNLOADED, MISROUTED, EXCEPTION)),
            Map.entry(UNLOADED, Set.of(LOADED, AWAITING_CLAIM, MISROUTED, EXCEPTION)),
            Map.entry(AWAITING_CLAIM, Set.of(DELIVERED, EXCEPTION)),
            Map.entry(DELIVERED, Set.of()),
            // H5: 誤配送からの救済動線（再経路設計 / 緊急輸送による復帰）
            Map.entry(MISROUTED, Set.of(RECEIVED, LOADED, IN_TRANSIT)),
            Map.entry(EXCEPTION, Set.of(RECEIVED, LOADED, IN_TRANSIT))
    );

    public boolean canTransition(TransportStatus from, TransportStatus to) {
        if (from == null) {
            throw new IllegalArgumentException("from は必須です");
        }
        if (to == null) {
            throw new IllegalArgumentException("to は必須です");
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
