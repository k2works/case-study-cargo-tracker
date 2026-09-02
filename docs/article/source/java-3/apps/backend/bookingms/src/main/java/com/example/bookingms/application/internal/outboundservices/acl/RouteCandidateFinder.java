package com.example.bookingms.application.internal.outboundservices.acl;

import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import java.util.List;

/**
 * 経路候補を取りに行く出力ポート（US09・[ADR-019]）。
 *
 * <p><strong>返すのは Booking Context の型である。</strong>routingms の型（`TransitPath` /
 * `TransitEdge`）をここへ持ち込むと、相手のドメインの変更がこちらのコンパイルを壊す。
 * HTTP か gRPC かも、この宣言には現れない。変換は実装（infrastructure）が行う。
 *
 * <p>名前は他のポートと同じ規約に揃えている（何を頼むかで名付け、{@code Port} 接尾辞を
 * 付けない）。設計には 2 つの名前があったため、IT5 でこちらに統一した。
 */
public interface RouteCandidateFinder {

    /**
     * 条件に合う経路候補を、推奨順で取る。
     *
     * @return 候補。1 件も無ければ空のリスト（例外にしない。「経路が無い」は業務上ありうる答え）
     */
    List<CargoItinerary> find(RouteCandidateQuery query);
}
