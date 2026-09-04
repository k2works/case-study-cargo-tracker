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

    /**
     * 一覧。
     *
     * <p><b>{@code total} を返す。</b> ページの大きさで切れていることを画面が知らせ
     * られないと、載らなかった荷主は誰の目にも入らないまま残る。予約登録の選択肢も
     * この一覧から作るので、切れた荷主はその日から予約が取れなくなる。</p>
     */
    public record ShipperListView(List<ShipperView> items, int total) {
    }
}
