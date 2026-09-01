package com.example.handlingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CustomsDeclarationMapper {

    String COLUMNS = """
            id, declaration_number, booking_id, tracking_number,
            declared_at, status, cleared_at, remarks, simulated
            """;

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO customs_declaration (
                declaration_number, booking_id, tracking_number, declared_at, status,
                cleared_at, remarks, simulated)
            VALUES (
                #{declarationNumber}, #{bookingId}, #{trackingNumber}, #{declaredAt}, #{status},
                #{clearedAt}, #{remarks}, #{simulated})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(CustomsDeclarationRecord row);

    /** **常に INSERT する save にしない。**状態の更新はこちらで書く。 */
    @Update("""
            UPDATE customs_declaration
               SET status = #{status},
                   cleared_at = #{clearedAt},
                   remarks = #{remarks},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    void updateStatus(CustomsDeclarationRecord row);

    @Select("SELECT " + COLUMNS + " FROM customs_declaration WHERE id = #{id}")
    @Results(id = "customsResult", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "declaration_number", property = "declarationNumber"),
        @Result(column = "booking_id", property = "bookingId"),
        @Result(column = "tracking_number", property = "trackingNumber"),
        @Result(column = "declared_at", property = "declaredAt"),
        @Result(column = "status", property = "status"),
        @Result(column = "cleared_at", property = "clearedAt"),
        @Result(column = "remarks", property = "remarks"),
    })
    CustomsDeclarationRecord findById(@Param("id") long id);

    /**
     * その貨物の決着していない申告（[ADR-025] 決定 7）。
     *
     * <p>**高々 1 件である**ことを登録側が守るため、ここで「最新」を選ぶ必要はない。
     */
    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM customs_declaration
             WHERE tracking_number = #{trackingNumber}
               AND status IN ('PENDING', 'HELD')
             ORDER BY declared_at DESC
             LIMIT 1
            """)
    @org.apache.ibatis.annotations.ResultMap("customsResult")
    CustomsDeclarationRecord findUnsettledByTrackingNumber(
            @Param("trackingNumber") String trackingNumber);

    /** 引取のガードが引く（US29-3）。 */
    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM customs_declaration
             WHERE booking_id = #{bookingId}
             ORDER BY declared_at DESC, id DESC
             LIMIT 1
            """)
    @org.apache.ibatis.annotations.ResultMap("customsResult")
    CustomsDeclarationRecord findLatestByBookingId(@Param("bookingId") String bookingId);

    /**
     * 一覧・検索（US29-7）。
     *
     * <p><strong>絞り込みは SQL で行う。</strong>全件を読んでアプリ側で絞ると、
     * 件数が増えたときに一覧が開かなくなる。
     */
    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
              FROM customs_declaration
             <where>
               <if test="bookingId != null">AND booking_id = #{bookingId}</if>
               <if test="trackingNumber != null">AND tracking_number = #{trackingNumber}</if>
               <if test="status != null">AND status = #{status}</if>
               <if test="unsettledOnly">AND status IN ('PENDING', 'HELD')</if>
               <!--
                 **待ち行列から架空の申告を外す**（[ADR-030] 決定 3・TD-02）。
                 名指し（予約番号・追跡番号）のときは外さない——外すと
                 シミュレーション自身が引取のガードを越えられなくなる
               -->
               <if test="bookingId == null and trackingNumber == null">
                 AND simulated = FALSE
               </if>
             </where>
             ORDER BY declared_at DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("customsResult")
    List<CustomsDeclarationRecord> search(@Param("bookingId") String bookingId,
            @Param("trackingNumber") String trackingNumber, @Param("status") String status,
            @Param("unsettledOnly") boolean unsettledOnly, @Param("limit") int limit);

    /**
     * 同じ条件に合う<strong>総件数</strong>（US29-7）。
     *
     * <p><strong>上限で切ったことを黙らない。</strong>件数を知らせずに切ると、
     * 担当者は「一覧に出ていないから無い」と読む。予約一覧と同じ形にする。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
              FROM customs_declaration
             <where>
               <if test="bookingId != null">AND booking_id = #{bookingId}</if>
               <if test="trackingNumber != null">AND tracking_number = #{trackingNumber}</if>
               <if test="status != null">AND status = #{status}</if>
               <if test="unsettledOnly">AND status IN ('PENDING', 'HELD')</if>
               <!--
                 **待ち行列から架空の申告を外す**（[ADR-030] 決定 3・TD-02）。
                 名指し（予約番号・追跡番号）のときは外さない——外すと
                 シミュレーション自身が引取のガードを越えられなくなる
               -->
               <if test="bookingId == null and trackingNumber == null">
                 AND simulated = FALSE
               </if>
             </where>
            </script>
            """)
    long count(@Param("bookingId") String bookingId,
            @Param("trackingNumber") String trackingNumber, @Param("status") String status,
            @Param("unsettledOnly") boolean unsettledOnly);
}
