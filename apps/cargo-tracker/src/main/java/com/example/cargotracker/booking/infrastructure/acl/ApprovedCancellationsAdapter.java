package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.ApprovedCancellations;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

/**
 * {@link ApprovedCancellations} の実装（ACL のアダプタ。US30 / #515）。
 *
 * <p><strong>渡すのは素の値だけである。</strong> {@code CancellationRequest} や
 * {@code Location} をそのまま渡すと、荷役が Booking のドメインを参照することになる
 * （ArchUnit ルール 4）。
 *
 * <p><strong>一覧はまとめて 1 回で引く。</strong> 手配 1 件ごとに貨物や港を
 * 引き直すと、待ち行列が伸びるほど遅くなる — <strong>いちばん混んでいるときに、
 * いちばん遅い</strong>（IT13 の C4 / IT15 の P3 と同じ形）。
 *
 * <p><strong>マッパーは本アダプタの内側に置く</strong>（{@link AffectedBookingsAdapter} と
 * 同じ形）。集約の読み書きを担う {@code CancellationMapper} に相乗りさせない —
 * ACL が要る列が増えるたびに集約側のマッパーが太る。
 */
@Component
public class ApprovedCancellationsAdapter implements ApprovedCancellations {

    private final DischargeOrderMapper mapper;

    public ApprovedCancellationsAdapter(DischargeOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DischargeOrder> findApprovedDischarges() {
        return mapper.findApprovedDischarges().stream()
                .map(row -> new DischargeOrder(
                        row.getBookingId().toString(),
                        row.getTrackingNumber(),
                        row.getDischargeUnlocode(),
                        row.getDischargeName(),
                        row.getDecidedAt()))
                .toList();
    }

    /** 承認済みキャンセルの荷降し手配を読むマッパー。 */
    @Mapper
    public interface DischargeOrderMapper {

        /**
         * 承認済みキャンセルの荷降し手配。
         *
         * <p><strong>陸揚げ地の名前まで運ぶ。</strong> UN/LOCODE だけを渡すと、
         * 受け取った側が {@code location} を引き直すことになる — 共有マスタは
         * どの BC からも読んでよいが（ADR-015）、<strong>同じ問い合わせを 2 回する形</strong>になる。
         *
         * <p><strong>追跡番号は貨物が持つ。</strong> 作業員が手にしているのは追跡番号だけであり、
         * 予約 ID は紙にもラベルにも無い。
         *
         * <p><strong>承認したものだけを返す。</strong> 決着していない申請と却下した申請は
         * 陸揚げ地が {@code NULL} であり JOIN でも落ちるが、
         * <strong>JOIN が守っているのであって規則が守っているのではない</strong>状態にしない
         * （却下に陸揚げ地が入ることを DB は禁じていない）。
         *
         * <p>並びは<strong>承認の古い順</strong>。待たせている手配から捌く。
         */
        @Select("""
                SELECT c.booking_id AS bookingId,
                       g.tracking_number AS trackingNumber,
                       c.discharge_location_unlocode AS dischargeUnlocode,
                       l.name AS dischargeName,
                       c.decided_at AS decidedAt
                  FROM booking_cancellation c
                  JOIN cargo g ON g.booking_id = c.booking_id
                  JOIN location l ON l.unlocode = c.discharge_location_unlocode
                 WHERE c.status = 'APPROVED'
                 ORDER BY c.decided_at
                """)
        List<DischargeOrderRow> findApprovedDischarges();
    }

    /**
     * 荷降し手配の 1 行。
     *
     * <p>MyBatis がセッターで詰めるため<strong>レコードにできない</strong>。
     */
    public static class DischargeOrderRow {

        private UUID bookingId;
        private String trackingNumber;
        private String dischargeUnlocode;
        private String dischargeName;
        private Instant decidedAt;

        public UUID getBookingId() {
            return bookingId;
        }

        public void setBookingId(UUID bookingId) {
            this.bookingId = bookingId;
        }

        public String getTrackingNumber() {
            return trackingNumber;
        }

        public void setTrackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
        }

        public String getDischargeUnlocode() {
            return dischargeUnlocode;
        }

        public void setDischargeUnlocode(String dischargeUnlocode) {
            this.dischargeUnlocode = dischargeUnlocode;
        }

        public String getDischargeName() {
            return dischargeName;
        }

        public void setDischargeName(String dischargeName) {
            this.dischargeName = dischargeName;
        }

        public Instant getDecidedAt() {
            return decidedAt;
        }

        public void setDecidedAt(Instant decidedAt) {
            this.decidedAt = decidedAt;
        }
    }
}
