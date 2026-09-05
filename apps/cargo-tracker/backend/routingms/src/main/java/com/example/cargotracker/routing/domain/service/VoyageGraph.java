package com.example.cargotracker.routing.domain.service;

import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.shared.domain.location.Location;
import java.util.List;
import java.util.Set;

/**
 * 探索が見る航海の接続（US08）。
 *
 * <p><b>読み取りモデルである。</b> 経路候補はテーブルに持たず（data-model.md）、
 * 問い合わせのたびに {@code voyage} / {@code carrier_movement} から組む。</p>
 *
 * <p>インタフェースにしているのは、探索の判断を実 DB から切り離して確かめるため。
 * 組み立て方（出港済み・キャンセル済みを外す）は実装側の責務で、実 DB の統合
 * テストで固定する。</p>
 */
public interface VoyageGraph {

    /** その港から出る区間。出港済み・キャンセル済みの航海は含まない。 */
    List<TransitEdge> edgesFrom(Location location);

    /**
     * その航海が受け入れる貨物種別。
     *
     * <p><b>1 行も無い航海は一般貨物のみ</b>（不変条件 4 の既定）。空集合を返すと、
     * 種別を選ばなかった航海が候補から全部消える。</p>
     */
    Set<CargoType> acceptedCargoTypes(String voyageNumber);
}
