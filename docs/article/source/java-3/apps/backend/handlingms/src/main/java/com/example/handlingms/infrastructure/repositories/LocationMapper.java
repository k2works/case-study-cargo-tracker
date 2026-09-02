package com.example.handlingms.infrastructure.repositories;

import com.example.shared.domain.model.Location;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LocationMapper {

    @Select("SELECT unlocode, name FROM location WHERE unlocode = #{unLocode}")
    @Result(column = "unlocode", property = "unLocode")
    @Result(column = "name", property = "name")
    Location findByUnLocode(@Param("unLocode") String unLocode);

    /** 画面の選択肢に使う。作業場所を自由入力にしないための一覧である（US15-3）。 */
    @Select("SELECT unlocode, name FROM location ORDER BY unlocode")
    @Result(column = "unlocode", property = "unLocode")
    @Result(column = "name", property = "name")
    List<Location> findAll();
}
