package com.example.cargotracker.billing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 予約のもとになった見積（注 N12）。
 *
 * <p><b>見積を経ない予約では行が無い。</b> 呼ぶ側は {@code null} を普通の状態
 * として扱う。</p>
 */
@Mapper
public interface BookingQuotationMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "booking_id, quotation_id, quoted_amount, currency, "
            + "quoted_at, projected_at";

    /** 冪等に書く。少なくとも 1 回配送なので、同じイベントが 2 度届きうる。 */
    int upsert(QuotationRow row);

    @Select("SELECT " + COLUMNS + " FROM booking_quotation WHERE booking_id = #{bookingId}")
    QuotationRow findByBooking(@Param("bookingId") String bookingId);

    record QuotationRow(
            String bookingId,
            String quotationId,
            BigDecimal quotedAmount,
            String currency,
            Instant quotedAt,
            Instant projectedAt) {
    }
}
