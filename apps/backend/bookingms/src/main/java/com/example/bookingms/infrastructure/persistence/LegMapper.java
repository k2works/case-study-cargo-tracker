package com.example.bookingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 旅程の輸送区間（US09）。 */
@Mapper
public interface LegMapper {

    @Insert("""
            INSERT INTO leg (
                cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                load_time, unload_time, seq_number
            ) VALUES (
                #{cargoId}, #{voyageNumber}, #{loadLocationUnlocode}, #{unloadLocationUnlocode},
                #{loadTime}, #{unloadTime}, #{seqNumber}
            )
            """)
    void insert(LegRecord row);

    /**
     * 旅程の差し替えは「消してから入れ直す」（IT3 の航海スケジュールと同じ判断）。
     *
     * <p>差分更新は順序の付け替えが要り、途中で失敗するとつながっていない旅程が残る。
     */
    @Delete("DELETE FROM leg WHERE cargo_id = #{cargoId}")
    void deleteByCargoId(@Param("cargoId") Long cargoId);

    /** 区間は順序に意味がある。並びが崩れると別の旅程になる。 */
    @Select("""
            SELECT l.id, l.cargo_id, l.voyage_number,
                   l.load_location_unlocode, lo.name AS load_location_name,
                   l.unload_location_unlocode, ul.name AS unload_location_name,
                   l.load_time, l.unload_time, l.seq_number
              FROM leg l
              JOIN location lo ON lo.unlocode = l.load_location_unlocode
              JOIN location ul ON ul.unlocode = l.unload_location_unlocode
             WHERE l.cargo_id = #{cargoId}
             ORDER BY l.seq_number
            """)
    // 列名と項目名の対応は明示する（この設定に頼らない方針を他の Mapper と揃える）
    @Results(id = "legResult", value = {
        @Result(column = "cargo_id", property = "cargoId"),
        @Result(column = "voyage_number", property = "voyageNumber"),
        @Result(column = "load_location_unlocode", property = "loadLocationUnlocode"),
        @Result(column = "load_location_name", property = "loadLocationName"),
        @Result(column = "unload_location_unlocode", property = "unloadLocationUnlocode"),
        @Result(column = "unload_location_name", property = "unloadLocationName"),
        @Result(column = "load_time", property = "loadTime"),
        @Result(column = "unload_time", property = "unloadTime"),
        @Result(column = "seq_number", property = "seqNumber"),
    })
    List<LegRecord> findByCargoId(@Param("cargoId") Long cargoId);
}
