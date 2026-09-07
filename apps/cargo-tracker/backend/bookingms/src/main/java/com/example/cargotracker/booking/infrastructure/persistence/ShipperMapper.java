package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 荷主の投影テーブル。 */
@Mapper
public interface ShipperMapper {

    /** 荷主コードは投影側で採番する。集約で MAX+1 しない（data-model.md）。 */
    @Select("SELECT 'SHP-' || lpad(nextval('shipper_code_seq')::text, 6, '0')")
    String nextShipperCode();

    @Select("SELECT count(*) FROM shipper WHERE email = #{email}")
    int countByEmail(@Param("email") String email);

    /**
     * 絞り込みに合う件数。一覧が上限で切れていることを画面が知らせるために使う。
     *
     * <p><b>絞り込み後の件数を返す。</b> 全件数を返すと、絞り込んだ画面の案内
     * （「N 件のうち M 件」）が嘘になる。</p>
     */
    int countAll(@Param("q") String q);

    /**
     * 重複相手の荷主 ID。要確認一覧が「既存の荷主を見る」の行き先に使う。
     *
     * <p>返すのは識別子だけ。メールアドレスは個人情報なので、応答に載せない。</p>
     */
    @Select("SELECT shipper_id FROM shipper WHERE email = #{email}")
    String findIdByEmail(@Param("email") String email);

    int insert(ShipperRow row);

    ShipperRow findById(@Param("shipperId") String shipperId);

    /**
     * 一覧（S10）。<b>名前で絞り込める</b>。
     *
     * <p><b>絞り込みが無いと、上限を超えた荷主にたどり着けない。</b> 一覧は荷主
     * コード順なので、新しく採った荷主ほど後ろに回る。件数が上限を超えると
     * 1 ページ目には決して出ず、<b>予約登録の選択肢にも出ない</b>ので、登録した
     * その日からその荷主の予約が取れなくなる（IT8 のクラスタで実測）。</p>
     *
     * <p>絞り込みは<b>部分一致・大文字小文字を問わない</b>。荷主名は手で打つので、
     * 完全一致を求めると探せない。</p>
     */
    java.util.List<ShipperRow> findAll(@Param("limit") int limit, @Param("offset") int offset,
            @Param("q") String q);

    /** 投影の行。個人情報の列は null になりうる（鍵破棄後）。 */
    record ShipperRow(
            String shipperId,
            String shipperCode,
            String shipperType,
            String name,
            String email,
            String phone,
            String address,
            String countryCode,
            String contractNumber,
            java.math.BigDecimal discountRate,
            Instant registeredAt,
            Instant projectedAt,
            String lastEventId) {
    }
}
