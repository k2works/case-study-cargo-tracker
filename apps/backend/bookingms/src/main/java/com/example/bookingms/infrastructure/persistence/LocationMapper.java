package com.example.bookingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LocationMapper {

    @Select("SELECT unlocode, name, country_code, time_zone FROM location ORDER BY unlocode")
    @Results(id = "locationList", value = {
        @Result(column = "country_code", property = "countryCode"),
        @Result(column = "time_zone", property = "timeZone")
    })
    List<LocationRecord> findAll();

    @Select("""
            SELECT unlocode, name, country_code, time_zone
            FROM location WHERE unlocode = #{unLocode}
            """)
    @Results(id = "locationByCode", value = {
        @Result(column = "country_code", property = "countryCode"),
        @Result(column = "time_zone", property = "timeZone")
    })
    LocationRecord findByUnLocode(@Param("unLocode") String unLocode);
}
