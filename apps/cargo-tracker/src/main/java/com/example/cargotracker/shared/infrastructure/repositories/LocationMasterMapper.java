package com.example.cargotracker.shared.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 港マスタの読み取り。
 *
 * <p><strong>置き場所は共有カーネル側の技術基盤である。</strong> {@code Location} は
 * ADR-005 が定める共有カーネルの要素であり、その実体である港マスタを読む必要は
 * Routing にも Booking にもある。どちらか一方の BC に置くと、他方が
 * <strong>同じ SQL をもう 1 本持つ</strong>か、BC 間の直接参照になる。
 *
 * <p>共有カーネルの範囲（ArchUnit ルール 6）が縛るのは {@code shared.domain.model} で
 * あり、本クラスはそこには含まれない。
 */
@Mapper
public interface LocationMasterMapper {

    /** 指定した UN/LOCODE のうち、マスタに存在するものを返す。 */
    @Select("""
            <script>
            SELECT unlocode FROM location
             WHERE unlocode IN
            <foreach item="code" collection="codes" open="(" separator="," close=")">
              #{code}
            </foreach>
            </script>
            """)
    List<String> findExisting(@Param("codes") List<String> codes);
}
