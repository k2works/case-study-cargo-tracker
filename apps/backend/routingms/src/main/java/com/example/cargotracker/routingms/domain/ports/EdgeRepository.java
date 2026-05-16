package com.example.cargotracker.routingms.domain.ports;

import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;

import java.util.List;

/**
 * 経路エッジデータの取得ポート（ADR-0011）。
 *
 * <p>voyage + carrier_movement の JOIN クエリは MyBatis の実装クラスに閉じ込め、
 * ドメイン層は本インターフェースのみに依存する。</p>
 */
public interface EdgeRepository {

    /**
     * 全エッジ（航海区間）を取得する。
     */
    List<TransitEdge> findAll();
}
