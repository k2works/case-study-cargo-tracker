package com.example.cargotracker.booking.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * キャンセル申請の読み書き（US30）。
 *
 * <p><strong>触るのは Booking のテーブルだけである</strong>（ADR-015）。
 * 荷主名や現在地が要る場合は ACL ポートで受け取る。
 */
@Mapper
public interface CancellationMapper {

    /**
     * 申請 1 行の読み出し。
     *
     * <p><strong>同じ列並びを書き写さない。</strong> 列を足すたびに書き足し忘れた
     * 1 か所だけが古くなり、その経路でだけ値が消える。
     */
    String SELECT_CANCELLATION = """
            SELECT c.id, c.booking_id AS bookingId, c.reason,
                   c.requested_by AS requestedBy, c.requested_at AS requestedAt,
                   c.status, c.fee_rate AS feeRate,
                   c.discharge_location_unlocode AS dischargeLocationUnlocode,
                   c.decided_by AS decidedBy, c.decided_at AS decidedAt,
                   c.decision_reason AS decisionReason, c.version
              FROM booking_cancellation c
            """;

    @Insert("""
            INSERT INTO booking_cancellation (
                booking_id, reason, requested_by, requested_at,
                status, fee_rate, version)
            VALUES (
                #{bookingId}, #{reason}, #{requestedBy}, #{requestedAt},
                'PENDING', #{feeRate}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CancellationRecord row);

    /**
     * 決定を保存する（楽観的ロック）。
     *
     * <p><strong>WHERE に承認待ちを書く</strong> — ドメインの守りと同じ条件を
     * SQL にも置く。集約を通らない経路が生まれても、決着した申請は動かない。
     */
    @Update("""
            UPDATE booking_cancellation
               SET status                      = #{status},
                   discharge_location_unlocode = #{dischargeLocationUnlocode},
                   decided_by                  = #{decidedBy},
                   decided_at                  = #{decidedAt},
                   decision_reason             = #{decisionReason},
                   version                     = version + 1,
                   updated_at                  = CURRENT_TIMESTAMP
             WHERE id      = #{id}
               AND version = #{version}
               AND status  = 'PENDING'
            """)
    int update(CancellationRecord row);

    @Select(SELECT_CANCELLATION + " WHERE c.id = #{id}")
    CancellationRecord findById(@Param("id") long id);

    /** 決着していない申請（<strong>古い順</strong>。待たせている申請から捌く）。 */
    @Select(SELECT_CANCELLATION
            + " WHERE c.status = 'PENDING' ORDER BY c.requested_at, c.id")
    List<CancellationRecord> findPending();

    /** 決着していない申請の件数（<strong>一覧を組み立てずに数える</strong>）。 */
    @Select("SELECT COUNT(*) FROM booking_cancellation WHERE status = 'PENDING'")
    int countPending();

    /** 予約に紐づく申請（<strong>新しい順</strong>）。<strong>却下も残す。</strong> */
    @Select(SELECT_CANCELLATION
            + " WHERE c.booking_id = #{bookingId} ORDER BY c.requested_at DESC, c.id DESC")
    List<CancellationRecord> findByBookingId(@Param("bookingId") UUID bookingId);

    /** 決着していない申請があるか（<strong>業務の言葉で拒むために引く</strong>）。 */
    @Select("""
            SELECT COUNT(*) FROM booking_cancellation
             WHERE booking_id = #{bookingId} AND status = 'PENDING'
            """)
    int countPendingFor(@Param("bookingId") UUID bookingId);
}
