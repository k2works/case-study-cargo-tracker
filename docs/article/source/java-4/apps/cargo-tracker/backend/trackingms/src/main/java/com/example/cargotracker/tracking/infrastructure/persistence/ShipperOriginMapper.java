package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷主がシミュレーション由来かどうかの写し（[ADR-0020] 決定 4）。
 *
 * <p><b>印だけを持つ。</b> 氏名も連絡先も写さない——追跡に要るのは
 * 「業務の一覧から外すかどうか」だけで、要らない個人情報を BC をまたいで
 * 増やす理由が無い。</p>
 */
@Mapper
public interface ShipperOriginMapper {

    /**
     * 写しを入れ直す。<b>リプレイで行が増えない形</b>（荷主 ID が主キー）。
     *
     * <p>訂正で印が変わることは無いが、上書きにしておけば投影を読み直しても
     * 最後のイベントの内容に落ち着く。</p>
     */
    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO shipper_origin (shipper_id, simulated, projected_at) "
            + "VALUES (#{shipperId}, #{simulated}, #{projectedAt}) "
            + "ON CONFLICT (shipper_id) DO UPDATE SET "
            + "  simulated = EXCLUDED.simulated, projected_at = EXCLUDED.projected_at")
    int upsert(@Param("shipperId") String shipperId,
            @Param("simulated") boolean simulated,
            @Param("projectedAt") Instant projectedAt);

    @Select("SELECT simulated FROM shipper_origin WHERE shipper_id = #{shipperId}")
    Boolean findSimulated(@Param("shipperId") String shipperId);
}
