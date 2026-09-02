package com.example.cargotracker.booking.infrastructure.query;

import java.math.BigDecimal;
import java.util.List;

/** 荷主の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class ShipperQueries {

    private ShipperQueries() {
    }

    /** 登録前の存在確認（一意の三段の 1 段目）。 */
    public record ExistsShipperEmailQuery(String email) {
    }

    public record FindShipperQuery(String shipperId) {
    }

    public record FindShippersQuery(int page, int size) {
    }

    /**
     * 画面に出す荷主。
     *
     * <p>個人情報は鍵破棄後に {@code null} になる。画面には「（削除済み）」と出すが、
     * それを決めるのは表示側で、読み取りモデルは null のまま運ぶ。</p>
     */
    public record ShipperView(
            String shipperId,
            String shipperCode,
            String shipperType,
            String name,
            String email,
            String phone,
            String address,
            String contractNumber,
            BigDecimal discountRate) {
    }

    public record ShipperListView(List<ShipperView> items) {
    }
}
