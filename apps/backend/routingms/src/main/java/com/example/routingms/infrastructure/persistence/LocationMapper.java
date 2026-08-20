package com.example.routingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LocationMapper {

    @Select("SELECT unlocode, name FROM location ORDER BY unlocode")
    List<LocationRecord> findAll();

    @Select("SELECT unlocode, name FROM location WHERE unlocode = #{unLocode}")
    LocationRecord findByUnLocode(@Param("unLocode") String unLocode);
}
