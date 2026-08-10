package com.example.cargotracker.billing.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 督促の記録の読み書き（IT14 レビュー C3）。
 *
 * <p><strong>触るのは Billing のテーブルだけである</strong>（ADR-015）。
 */
@Mapper
public interface ReminderMapper {

    /**
     * 督促を記録する。
     *
     * <p><strong>請求番号から請求書 ID を引くのは同じ文の中で行う。</strong>
     * 2 文に分けると、その間に請求書が消えた場合に外部キー違反で 500 になる。
     */
    @Insert("""
            INSERT INTO invoice_reminder (invoice_id, reminded_at, reminded_by, note)
            SELECT i.id, #{remindedAt}, #{remindedBy}, #{note}
              FROM invoice i
             WHERE i.invoice_number = #{invoiceNumber}
            """)
    int insert(ReminderRecord row);

    /** 督促の記録（<strong>新しい順</strong>）。 */
    @Select("""
            SELECT r.reminded_at AS remindedAt, r.reminded_by AS remindedBy, r.note
              FROM invoice_reminder r
              JOIN invoice i ON i.id = r.invoice_id
             WHERE i.invoice_number = #{invoiceNumber}
             ORDER BY r.reminded_at DESC, r.id DESC
            """)
    List<ReminderRecord> findByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);
}
