package com.example.cargotracker.routing.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 港マスタの読み取り。 */
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
