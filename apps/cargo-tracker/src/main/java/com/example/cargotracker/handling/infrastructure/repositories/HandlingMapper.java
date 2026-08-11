package com.example.cargotracker.handling.infrastructure.repositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷役作業の MyBatis マッパー。
 *
 * <p><strong>UUID の型ハンドラは明示する。</strong> 実行時は設定で解決されるが、
 * 設定を読まない道具（JIG）からは解決できず、このマッパーだけが読み飛ばされる
 * （IT5 の P5 で実測）。
 */
@Mapper
public interface HandlingMapper {

    @Insert("""
            INSERT INTO handling_activity (
                booking_id, event_type, event_completion_time,
                location_unlocode, voyage_number, tracking_number,
                claim_confirmation_method, claim_confirmation_code, claim_consignee_name,
                note, operator_name, version)
            VALUES (
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{eventType}, #{eventCompletionTime},
                #{locationUnlocode}, #{voyageNumber}, #{trackingNumber},
                #{claimConfirmationMethod}, #{claimConfirmationCode}, #{claimConsigneeName},
                #{note}, #{operatorName}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HandlingActivityRecord row);

    /**
     * 予約ごとの荷役履歴を新しい順で返す。
     *
     * <p><strong>ORDER BY を外さない。</strong> 履歴は「最後に何が起きたか」を
     * 読むためのものであり、順序が崩れると現在地が読めなくなる。
     */
    @Select("""
            SELECT id, booking_id, event_type, event_completion_time,
                   location_unlocode, voyage_number, tracking_number,
                   claim_confirmation_method, claim_confirmation_code, claim_consignee_name,
                   note, operator_name, version
              FROM handling_activity
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
             ORDER BY event_completion_time DESC
            """)
    List<HandlingActivityRecord> findByBookingId(@Param("bookingId") UUID bookingId);

    /**
     * すでに荷降し（{@code UNLOAD}）を記録した予約（US30。荷降し手配の絞り込み）。
     *
     * <p><strong>まとめて 1 回で引く。</strong> 手配 1 件ごとに問い合わせると、
     * 待ち行列が伸びるほど遅くなる — <strong>いちばん混んでいるときに、いちばん遅い</strong>
     * （IT13 の C4 / IT15 の P3）。
     *
     * <p><strong>取り消された記録は数えない。</strong> 取り消したなら降ろしていない。
     */
    @Select("""
            <script>
            SELECT DISTINCT booking_id
              FROM handling_activity
             WHERE event_type = 'UNLOAD'
               AND cancelled_at IS NULL
               AND booking_id IN
            <foreach item="id" collection="bookingIds" open="(" separator="," close=")">
              #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            </foreach>
            </script>
            """)
    List<UUID> findUnloadedBookingIds(@Param("bookingIds") List<UUID> bookingIds);

    /**
     * 荷役 ID から追跡番号をまとめて引く（R5）。
     *
     * <p><strong>1 件ずつ引き直さない。</strong> 訂正の承認待ち一覧は 1 行ごとに
     * 荷役を開いており、<strong>待ち行列が伸びるほど遅くなっていた</strong> —
     * いちばん混んでいるときに、いちばん遅い。
     */
    @Select("""
            <script>
            SELECT id, tracking_number
              FROM handling_activity
             WHERE id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    @MapKey("id")
    Map<Long, Map<String, Object>> findTrackingNumbersByIds(@Param("ids") List<Long> ids);

    /**
     * 荷役履歴を新しい順で返す（荷役作業一覧）。
     *
     * <p><strong>貨物種別を一緒に読む（US05）。</strong> 現物に触る作業員が
     * 危険物・冷凍だと気づけないなら、申告を登録した意味が半分になる。
     * **BC をまたぐ直接参照ではない** — 読み取り側の SQL である
     * （{@code MyBatisBookingQueryService} が荷主名を JOIN するのと同じ理由）。
     */
    @Select("""
            SELECT h.id, h.booking_id, h.event_type, h.event_completion_time,
                   h.location_unlocode, h.voyage_number, h.tracking_number,
                   h.claim_confirmation_method, h.claim_confirmation_code,
                   h.claim_consignee_name,
                   h.note, h.operator_name, h.version,
                   h.cancelled_at AS cancelledAt, h.cancelled_by AS cancelledBy,
                   c.cargo_type AS cargoType
              FROM handling_activity h
              LEFT JOIN cargo c ON c.booking_id = h.booking_id
             ORDER BY h.event_completion_time DESC, h.id DESC
             LIMIT #{limit}
            """)
    List<HandlingActivityRecord> findRecent(@Param("limit") int limit);

    /** 1 件の荷役（訂正・取り消しの対象。US36）。 */
    @Select("""
            SELECT id, booking_id, event_type, event_completion_time,
                   location_unlocode, voyage_number, tracking_number,
                   claim_confirmation_method, claim_confirmation_code, claim_consignee_name,
                   note, operator_name, version,
                   cancelled_at AS cancelledAt, cancelled_by AS cancelledBy
              FROM handling_activity
             WHERE id = #{id}
            """)
    HandlingActivityRecord findById(@Param("id") long id);

    /**
     * 取り消された事実を書く（US36）。
     *
     * <p><strong>行は消さない。</strong> 誰がいつ何を登録し、誰がいつ取り消したかが
     * 読めなくなると、事故時に経緯を追えない。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE handling_activity
               SET cancelled_at = #{cancelledAt},
                   cancelled_by = #{cancelledBy},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND cancelled_at IS NULL
            """)
    int markCancelled(HandlingActivityRecord row);

    /**
     * 訂正を反映する（US36）。
     *
     * <p><strong>取り消しと違い、行は生きたままである。</strong> 直すのは記録の中身で
     * あって、引き渡したという事実ではない。
     *
     * <p><strong>NULL の項目は変えない。</strong> 作業日時だけを直す申請で、
     * メモまで空になってはならない。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE handling_activity
               SET event_completion_time =
                       COALESCE(#{eventCompletionTime}, event_completion_time),
                   note = COALESCE(#{note}, note),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND cancelled_at IS NULL
            """)
    int applyCorrection(HandlingActivityRecord row);
}
