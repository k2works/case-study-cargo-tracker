package com.example.trackingms.infrastructure.repositories;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 荷主へ届ける知らせと、読んだ位置（US39）。 */
@Mapper
public interface ShipperNoticeMapper {

    /**
     * その貨物たちへの、読んだ位置より新しい知らせ。
     *
     * <p><strong>古い順に返す。</strong>ポップアップは起きた順に出さないと、
     * 出港より先に到着が出る。
     *
     * <p><strong>絞り込みは SQL に置く。</strong>読んでから絞ると、貨物が増えた荷主ほど
     * 窓の外に落ちる知らせが出る——件数だけで壊れる形である。
     */
    @Select("""
            <script>
            SELECT id, tracking_number, message, noticed_at
              FROM tracking_notice
             WHERE id &gt; #{lastNoticeId}
               AND tracking_number IN
                   <foreach item='number' collection='trackingNumbers'
                            open='(' separator=',' close=')'>#{number}</foreach>
             ORDER BY id ASC
             LIMIT #{limit}
            </script>
            """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "tracking_number", property = "trackingNumber"),
            @Result(column = "message", property = "message"),
            @Result(column = "noticed_at", property = "noticedAt"),
    })
    List<ShipperNoticeRecord> findNewerThan(
            @Param("trackingNumbers") List<String> trackingNumbers,
            @Param("lastNoticeId") long lastNoticeId, @Param("limit") int limit);

    @Select("SELECT last_notice_id FROM shipper_notice_ack WHERE username = #{username}")
    Long selectWatermark(@Param("username") String username);

    /**
     * 読んだ位置を進める。
     *
     * <p><strong>条件で守る。</strong>{@code last_notice_id < #{lastNoticeId}} を置くことで、
     * 後着の古い値では 0 行しか動かない——ドメイン側（{@code NoticeWatermark#advanceTo}）と
     * <strong>同じ規則を、同じ変更で SQL にも置く</strong>。
     *
     * <p><strong>{@code ON CONFLICT} を使わない。</strong>PostgreSQL では通るが H2 では
     * 構文誤りになる（方言スモークが捕まえた）。ローカルの起動だけが落ちる形を作らない。
     *
     * @return 動いた行数。0 なら「行が無い」か「すでに先へ進んでいる」
     */
    @Update("""
            UPDATE shipper_notice_ack
               SET last_notice_id = #{lastNoticeId}, updated_at = #{updatedAt}
             WHERE username = #{username}
               AND last_notice_id < #{lastNoticeId}
            """)
    int advanceWatermark(@Param("username") String username,
            @Param("lastNoticeId") long lastNoticeId, @Param("updatedAt") Instant updatedAt);

    /**
     * 初めて読んだときの 1 行。
     *
     * <p><strong>衝突は一意制約に裁かせる</strong>（IT15 の実行 ID と同じ形）。
     * 「無いことを確かめてから入れる」の間に別の要求が入れると、確かめた側が勝ってしまう。
     */
    @Insert("""
            INSERT INTO shipper_notice_ack (username, last_notice_id, updated_at)
            VALUES (#{username}, #{lastNoticeId}, #{updatedAt})
            """)
    void insertWatermark(@Param("username") String username,
            @Param("lastNoticeId") long lastNoticeId, @Param("updatedAt") Instant updatedAt);
}
