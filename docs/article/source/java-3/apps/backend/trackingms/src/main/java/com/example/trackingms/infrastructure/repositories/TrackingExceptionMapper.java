package com.example.trackingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 起票された例外（{@code tracking_exception_event}）。 */
@Mapper
public interface TrackingExceptionMapper {

    String COLUMNS = """
            id, tracking_number, exception_type, description,
            occurred_at, resolved_at, resolution_notes
            """;

    @Insert("""
            INSERT INTO tracking_exception_event (
                tracking_number, exception_type, description, occurred_at)
            VALUES (#{trackingNumber}, #{exceptionType}, #{description}, #{occurredAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(TrackingExceptionRecord row);

    /**
     * 解決したことを足す。<strong>消さない</strong>。
     *
     * <p>すでに解決している行は動かさない——2 回目の解決で対応内容が上書きされると、
     * 最初に何をしたかが失われる。
     */
    @Update("""
            UPDATE tracking_exception_event
               SET resolved_at = #{resolvedAt}, resolution_notes = #{resolutionNotes},
                   updated_at = NOW()
             WHERE id = #{id} AND resolved_at IS NULL
            """)
    void resolve(TrackingExceptionRecord row);

    /** 未解決の例外。<strong>1 件までである</strong>（[ADR-024] 決定 2）。 */
    @Select("SELECT " + COLUMNS + """
             FROM tracking_exception_event
             WHERE tracking_number = #{trackingNumber} AND resolved_at IS NULL
             ORDER BY id DESC
             LIMIT 1
            """)
    @Results(id = "exceptionResult", value = {
        @Result(column = "tracking_number", property = "trackingNumber"),
        @Result(column = "exception_type", property = "exceptionType"),
        @Result(column = "occurred_at", property = "occurredAt"),
        @Result(column = "resolved_at", property = "resolvedAt"),
        @Result(column = "resolution_notes", property = "resolutionNotes")
    })
    TrackingExceptionRecord findOpen(@Param("trackingNumber") String trackingNumber);

    /**
     * 1 つの貨物に起きた例外を、解決済みも含めて古い順に返す（US19-5）。
     *
     * <p><strong>解決しても消さない</strong>ので、ここで読み出せる。
     */
    @Select("SELECT " + COLUMNS + """
             FROM tracking_exception_event
             WHERE tracking_number = #{trackingNumber}
             ORDER BY occurred_at, id
             LIMIT #{limit}
            """)
    @org.apache.ibatis.annotations.ResultMap("exceptionResult")
    List<TrackingExceptionRecord> findByTrackingNumber(
            @Param("trackingNumber") String trackingNumber, @Param("limit") int limit);

    /**
     * 未解決の例外がある追跡番号（横断規約）。
     *
     * <p><strong>絞り込みは SQL で行う。</strong>全件読んで Java で数えると、件数が
     * 増えた日に一覧の重さを引き継ぐ。
     */
    @Select("""
            SELECT tracking_number FROM tracking_exception_event
             WHERE resolved_at IS NULL
             ORDER BY id
             LIMIT #{limit}
            """)
    List<String> findOpenTrackingNumbers(@Param("limit") int limit);
}
