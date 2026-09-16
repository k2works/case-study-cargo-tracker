package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷主への知らせ（US37 §受入基準 1・3・4）。
 *
 * <p><b>絞るのは SQL。</b> 全件を読んでから捨てると、他社の追跡がメモリに載り、
 * 絞り忘れがそのまま情報漏れになる。</p>
 *
 * <p><b>既読はサーバが持つ</b>（§3）。ブラウザに持つと、荷主が端末を使い分けた
 * とき同じ知らせが行く先々でもう一度出る。</p>
 */
@Mapper
public interface NoticeMapper {

    /**
     * 未読の知らせ（新しい順）。
     *
     * <p><b>荷主で絞るには結合が要る</b>（注 N3。着手前に実測した）——
     * {@code tracking_event} は荷主を持たないので {@code tracking_summary} と
     * 突き合わせる。</p>
     *
     * <p><b>シミュレーション由来は外さない。</b> 行はその荷主のものしか返らない
     * ので本物に混ざりようがなく、外すと確認用の利用者が知らせを受け取れない
     * （US37 §6・[ADR-0020] 決定 4 の「外さない読み口」）。</p>
     *
     * <p><b>古い順に返す。</b> 新しい順だと、上限で切ったときに返る 20 件の
     * 最大値が<b>未読全体の最大値</b>になる——それを既読にすると、出していない
     * 古い知らせまで黙って読んだことになる（IT17 のレビューで実測）。
     * 古い順なら、既読にするのは<b>出した分ちょうど</b>である。</p>
     */
    @Select("SELECT e.sequence_no, e.tracking_number, e.event_type, e.new_status, "
            + "e.location, e.occurred_at, s.origin_unlocode, s.destination_unlocode "
            + "FROM tracking_event e "
            + "JOIN tracking_summary s ON s.tracking_number = e.tracking_number "
            + "WHERE s.shipper_id = #{shipperId} "
            + "  AND e.sequence_no > COALESCE("
            + "      (SELECT last_read_sequence FROM notice_read_position "
            + "        WHERE shipper_id = #{shipperId}), 0) "
            + "ORDER BY e.sequence_no ASC "
            + "LIMIT #{limit}")
    List<NoticeRow> findUnread(@Param("shipperId") String shipperId,
            @Param("limit") int limit);

    /**
     * 既読の位置を進める（§3）。
     *
     * <p><b>戻さない。</b> 別の端末が先に読んでいれば、そちらの位置が正しい
     * ——古い画面の「ここまで読んだ」で巻き戻すと、同じ知らせがもう一度出る。</p>
     */
    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO notice_read_position (shipper_id, last_read_sequence, updated_at) "
            + "VALUES (#{shipperId}, #{sequenceNo}, #{updatedAt}) "
            + "ON CONFLICT (shipper_id) DO UPDATE SET "
            + "  last_read_sequence = GREATEST("
            + "      notice_read_position.last_read_sequence, EXCLUDED.last_read_sequence), "
            + "  updated_at = EXCLUDED.updated_at")
    int markRead(@Param("shipperId") String shipperId,
            @Param("sequenceNo") long sequenceNo,
            @Param("updatedAt") Instant updatedAt);

    @Select("SELECT last_read_sequence FROM notice_read_position WHERE shipper_id = #{shipperId}")
    Long findReadPosition(@Param("shipperId") String shipperId);

    /**
     * その荷主のいちばん新しい知らせの位置（無ければ {@code null}）。
     *
     * <p><b>初めて開いた荷主の起点になる。</b> 既読位置が無いと過去の履歴が
     * すべて未読になり、何か月も前の積込・荷降しがポップアップで出る——
     * 「新しいことが起きたら知らせる」という約束と逆である
     * （IT17 のレビューで実測）。</p>
     */
    @Select("SELECT max(e.sequence_no) FROM tracking_event e "
            + "JOIN tracking_summary s ON s.tracking_number = e.tracking_number "
            + "WHERE s.shipper_id = #{shipperId}")
    Long findLatestSequence(@Param("shipperId") String shipperId);

    /** 知らせ 1 件。<b>金額も社内メモも入らない</b>（荷主に出すのは状態だけ）。 */
    record NoticeRow(
            long sequenceNo,
            String trackingNumber,
            String eventType,
            String newStatus,
            String location,
            Instant occurredAt,
            String originUnlocode,
            String destinationUnlocode) {
    }
}
