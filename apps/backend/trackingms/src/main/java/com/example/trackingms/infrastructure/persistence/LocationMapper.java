package com.example.trackingms.infrastructure.persistence;

import com.example.shared.domain.model.Location;
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
}
