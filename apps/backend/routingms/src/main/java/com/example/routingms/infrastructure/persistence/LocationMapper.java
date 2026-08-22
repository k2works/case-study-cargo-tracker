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

    /**
     * 地点の業務タイムゾーン（[ADR-010]）。
     *
     * <p>到着期限は<strong>目的地の暦</strong>で判断する。単一の業務タイムゾーンで判断すると、
     * 目的地が東西にずれた分だけ「当日」の範囲が bookingms とずれ、こちらが候補に出した経路を
     * 向こうが「期限を過ぎている」と断る（またはその逆で正当な便が消える）。
     */
    @Select("SELECT time_zone FROM location WHERE unlocode = #{unLocode}")
    String findTimeZoneOf(@Param("unLocode") String unLocode);
}
