package com.example.simulationms.infrastructure.repositories;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** simulation_session への SQL。 */
@Mapper
public interface ContinuousRunSessionMapper {

    String COLUMNS = " s.id, s.session_id, s.seed, s.interval_seconds, s.max_concurrent,"
            + " s.exception_ratio, s.status, s.started_by, s.started_at, s.stopped_at"
            + " FROM simulation_session s";

    @Insert("INSERT INTO simulation_session (session_id, seed, interval_seconds, max_concurrent,"
            + " exception_ratio, status, started_by, started_at, stopped_at)"
            + " VALUES (#{sessionId}, #{seed}, #{intervalSeconds}, #{maxConcurrent},"
            + " #{exceptionRatio}, #{status}, #{startedBy}, #{startedAt}, #{stoppedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ContinuousRunSessionRecord row);

    /**
     * 状態を書き換える。
     *
     * <p><strong>状態と停止時刻だけを書く。</strong>種と上限は開始時に決まり、
     * 変わらない——変えられる形にすると、記録した種で再現できなくなる。
     */
    @Update("UPDATE simulation_session SET status = #{status}, stopped_at = #{stoppedAt}"
            + " WHERE session_id = #{sessionId}")
    void updateStatus(ContinuousRunSessionRecord row);

    @Select("SELECT" + COLUMNS + " WHERE s.session_id = #{sessionId}")
    @Results(id = "sessionResult", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "session_id", property = "sessionId"),
        @Result(column = "seed", property = "seed"),
        @Result(column = "interval_seconds", property = "intervalSeconds"),
        @Result(column = "max_concurrent", property = "maxConcurrent"),
        @Result(column = "exception_ratio", property = "exceptionRatio"),
        @Result(column = "status", property = "status"),
        @Result(column = "started_by", property = "startedBy"),
        @Result(column = "started_at", property = "startedAt"),
        @Result(column = "stopped_at", property = "stoppedAt")
    })
    ContinuousRunSessionRecord findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 動いているセッション。
     *
     * <p><strong>停止済みは含めない。</strong>含めると、止めたはずのセッションが
     * また刻み始める。
     */
    /**
     * 直近のセッション（新しい順・TD-03）。
     *
     * <p><strong>停止したものも返す。</strong>停止した瞬間に種が読めなくなると、
     * 翌朝には落ちた並びを再現する手立てが無い。
     */
    @Select("SELECT" + COLUMNS + " ORDER BY s.id DESC LIMIT #{limit}")
    @ResultMap("sessionResult")
    java.util.List<ContinuousRunSessionRecord> findRecent(@Param("limit") int limit);

    @Select("SELECT" + COLUMNS + " WHERE s.status <> 'STOPPED' ORDER BY s.id DESC LIMIT 1")
    @ResultMap("sessionResult")
    ContinuousRunSessionRecord findActive();

    @Select("SELECT COUNT(*) FROM simulation_session WHERE session_id LIKE #{prefix} || '%'")
    int countStartedOn(@Param("prefix") String prefix);
}
