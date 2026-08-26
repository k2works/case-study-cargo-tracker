package com.example.billingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 精算明細の永続化（[ADR-027] 決定 6）。 */
@Mapper
public interface InvoiceLineItemMapper {

    @Insert("""
            INSERT INTO invoice_line_item (invoice_id, description, amount_value,
                                           amount_currency, seq_number)
            VALUES (#{invoiceId}, #{description}, #{amountValue}, #{amountCurrency},
                    #{seqNumber})
            """)
    void insert(InvoiceLineItemRecord row);

    /** **積んだ順に読む。** 順序が変わると、根拠の並びが毎回変わって読みにくい。 */
    @Select("""
            SELECT id, invoice_id, description, amount_value, amount_currency, seq_number
              FROM invoice_line_item
             WHERE invoice_id = #{invoiceId}
             ORDER BY seq_number
            """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "invoice_id", property = "invoiceId"),
            @Result(column = "description", property = "description"),
            @Result(column = "amount_value", property = "amountValue"),
            @Result(column = "amount_currency", property = "amountCurrency"),
            @Result(column = "seq_number", property = "seqNumber"),
    })
    List<InvoiceLineItemRecord> selectByInvoiceId(@Param("invoiceId") Long invoiceId);
}
